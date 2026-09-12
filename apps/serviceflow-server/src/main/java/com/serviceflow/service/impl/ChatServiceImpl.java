package com.serviceflow.service.impl;

import static org.springframework.http.HttpStatus.CONFLICT;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.agent.CustomerWorkflow;
import com.serviceflow.agent.Intent;
import com.serviceflow.agent.ServiceFlowState;
import com.serviceflow.ai.AiGateway;
import com.serviceflow.exception.ErrorCode;
import com.serviceflow.exception.ServiceFlowException;
import com.serviceflow.infrastructure.redis.ChatMemory;
import com.serviceflow.model.ChatModels;
import com.serviceflow.security.CurrentPrincipal;
import com.serviceflow.service.AiAuditService;
import com.serviceflow.service.ChatService;
import com.serviceflow.service.ChatSessionService;
import com.serviceflow.service.impl.support.ChatRequestStore;
import com.serviceflow.web.sse.SseEventWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public final class ChatServiceImpl implements ChatService {
    private static final int ANSWER_CHUNK_SIZE = 28;
    private static final Duration GUEST_REQUEST_LOCK_TTL = Duration.ofMinutes(2);
    private static final String PRODUCT_SYSTEM_PROMPT = "你是 ServiceFlow 的商品售前与售后客服。"
            + "只能依据用户问题、结构化商品事实和文档证据回答。结构化商品事实对价格、型号、规格和销售状态具有最高优先级，"
            + "文档证据只用于说明功能、使用方法、适配、保修和注意事项。证据不足时明确说暂无可靠资料，不能凭常识补全。"
            + "不要编造库存、优惠、承诺或售后规则；不要输出推荐排序。用简洁中文回答，并优先回应当前问题。";

    private final ChatMemory memory;
    private final ChatSessionService sessions;
    private final ChatRequestStore requests;
    private final CustomerWorkflow workflow;
    private final AiGateway ai;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AiAuditService audit;
    private final SseEventWriter events;

    public ChatServiceImpl(
            ChatMemory memory,
            ChatSessionService sessions,
            ChatRequestStore requests,
            CustomerWorkflow workflow,
            AiGateway ai,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            AiAuditService audit,
            SseEventWriter events) {
        this.memory = memory;
        this.sessions = sessions;
        this.requests = requests;
        this.workflow = workflow;
        this.ai = ai;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.events = events;
    }

    /**
     * 处理一条聊天消息，并通过 SSE 持续向前端推送处理进度和回答。
     *
     * <p>进入 AI 工作流前，先校验会话归属并按 clientRequestId 处理重复请求，避免同一条消息被重复执行。
     */
    @Override
    public void stream(
            CurrentPrincipal principal, String sessionId, ChatModels.MessageRequest request, SseEmitter emitter) {
        // 请求中的 sessionId 是对外 UUID。登录用户需校验会话归属并换成 chat_session.id，供外键查询使用；
        // 访客会话只保存在 Redis，不存在数据库主键，因此 databaseId 使用 null。
        Long databaseId = principal.guest() ? null : sessions.requireCustomerSession(principal, sessionId);

        // 记录本次请求是否取得了访客锁，便于 finally 中只释放自己成功取得的锁。
        boolean guestLock = false;
        if (principal.guest()) {
            // 校验当前访客只能访问自己的 Redis 会话。
            sessions.requireGuestSession(principal, sessionId);

            // 相同 clientRequestId 已经完成时，直接重放旧结果，不再调用 AI。
            ChatMemory.CompletedResponse completed =
                    memory.completed(principal.subject(), sessionId, request.clientRequestId());
            if (completed != null) {
                replayGuest(emitter, completed);
                return;
            }

            // Redis 锁保证同一访客请求在并发到达时，只有一个请求获得执行权。
            acquireGuestLock(requestKey(principal, sessionId, request.clientRequestId()));
            guestLock = true;
            // 将用户消息加入 Redis 短期记忆，供后续构造 AI 上下文。
            memory.append(principal.subject(), sessionId, "USER", request.message());
        } else {
            // 在同一数据库事务中登记请求并保存用户消息；started=false 表示已有结果可重放。
            ChatRequestStore.BeginResult begin =
                    requests.begin(databaseId, request.clientRequestId(), request.message());
            if (!begin.started()) {
                replayCustomer(emitter, begin.completed());
                return;
            }
            // 数据库负责可靠持久化，Redis 中的副本用于快速组装近期对话上下文。
            memory.append(principal.subject(), sessionId, "USER", request.message());
        }

        long startedNanos = System.nanoTime();
        ServiceFlowState state = null;
        boolean audited = false;
        try {
            state = new ServiceFlowState(
                    sessionId,
                    principal,
                    request.message(),
                    request.pageContext() != null && request.pageContext().productId() != null
                            ? request.pageContext().productId()
                            : memory.activeProduct(principal.subject(), sessionId),
                    request.clientRequestId());
            // 暂时把工作流视为黑盒：输入本次请求状态，输出业务结果，并把过程中产生的事件转发给前端。
            CustomerWorkflow.Result result = workflow.execute(state, (name, data) -> events.send(emitter, name, data));
            if (state.productIds().size() == 1) {
                memory.setActiveProduct(
                        principal.subject(), sessionId, state.productIds().getFirst());
            }
            // 生成最终回答；生成期间每个文本片段已经通过 token 事件实时发送给前端。
            String answer = generateAnswer(state, result, request.message(), emitter);
            String messageId = UUID.randomUUID().toString();
            String metadata = writeJson(Map.of(
                    "citations", result.citations(), "intent", state.intent().name()));
            if (databaseId == null) {
                // 访客没有数据库会话，将完整结果保存在 Redis，供相同 clientRequestId 重放。
                memory.storeCompleted(
                        principal.subject(),
                        sessionId,
                        request.clientRequestId(),
                        new ChatMemory.CompletedResponse(
                                messageId, answer, state.intent().name(), result.citations(), result.degraded()));
            } else {
                // 登录用户将助手消息写入 MySQL，并把 chat_request 状态改为 COMPLETED。
                requests.complete(
                        databaseId,
                        request.clientRequestId(),
                        messageId,
                        answer,
                        state.intent().name(),
                        metadata);
            }
            // 无论访客还是登录用户，都在 Redis 近期对话中保存完整助手回答，供下一轮构造上下文。
            memory.append(principal.subject(), sessionId, "ASSISTANT", answer);
            audit.success(
                    databaseId, state, request.message(), answer, result.citations(), result.degraded(), startedNanos);
            audited = true;
            // token 事件传输正文；done 事件通知前端本轮回答已全部完成，并携带最终元数据。
            events.send(
                    emitter,
                    "done",
                    Map.of(
                            "messageId",
                            messageId,
                            "intent",
                            state.intent().name(),
                            "citations",
                            result.citations(),
                            "replayed",
                            false));
            emitter.complete();
        } catch (RuntimeException exception) {
            if (databaseId != null) {
                // 登录用户的失败请求会被标记为 FAILED，之后允许恢复执行。
                requests.fail(
                        databaseId,
                        request.clientRequestId(),
                        errorCode(exception).name());
            }
            if (!audited) {
                audit.failure(databaseId, request.clientRequestId(), state, request.message(), exception, startedNanos);
            }
            throw exception;
        } finally {
            if (guestLock) {
                // 无论成功或失败都释放访客请求锁，避免后续请求一直被判定为处理中。
                redis.delete(requestKey(principal, sessionId, request.clientRequestId()));
            }
        }
    }

    /**
     * 根据意图选择回答来源，并把所有来源统一转换成逐片段输出。
     *
     * <p>普通聊天和有可信上下文的回答来自 AI 流；已有的非流式结果则由后端主动切片。
     */
    private String generateAnswer(
            ServiceFlowState state, CustomerWorkflow.Result result, String userMessage, SseEmitter emitter) {
        StringBuilder answer = new StringBuilder();
        if (state.intent() == Intent.CHAT) {
            ai.streamAnswer(
                    "你是 ServiceFlow 客服，只回答售前商品和售后服务范围的问题。", userMessage, token -> appendToken(answer, emitter, token));
        } else if (result.groundingContext() != null
                && !result.groundingContext().isBlank()
                && ai.supportsGroundedGeneration()) {
            // 商品查询存在可信上下文时，再让大模型基于这些事实生成自然语言回答。
            ai.streamAnswer(
                    PRODUCT_SYSTEM_PROMPT,
                    productPrompt(state, userMessage, result.groundingContext()),
                    token -> appendToken(answer, emitter, token));
        } else {
            // 即使上游只有完整答案，也按固定长度切片，让前端保持相同的 SSE 消费方式。
            chunks(result.answer(), ANSWER_CHUNK_SIZE).forEach(token -> appendToken(answer, emitter, token));
        }
        return answer.toString();
    }

    /** 同时累积最终完整答案，并把当前文本片段作为 token 事件推送给浏览器。 */
    private void appendToken(StringBuilder answer, SseEmitter emitter, String token) {
        answer.append(token);
        events.send(emitter, "token", token);
    }

    private String productPrompt(ServiceFlowState state, String userMessage, String groundingContext) {
        // 最终用户提示词由最近对话、本轮问题和可信检索依据三部分组成。
        StringBuilder prompt = new StringBuilder("会话上下文：\n");
        List<Map<String, String>> history = memory.history(state.principal().subject(), state.sessionId());
        history.stream().skip(Math.max(0, history.size() - 8L)).forEach(item -> prompt.append(
                        item.getOrDefault("role", ""))
                .append('：')
                .append(item.getOrDefault("content", ""))
                .append('\n'));
        return prompt.append("\n当前用户问题：\n")
                .append(userMessage)
                .append("\n\n可信商品上下文：\n")
                .append(groundingContext)
                .toString();
    }

    private void replayGuest(SseEmitter emitter, ChatMemory.CompletedResponse completed) {
        events.send(
                emitter,
                "meta",
                Map.of(
                        "intent",
                        completed.intent(),
                        "degraded",
                        completed.degraded(),
                        "citations",
                        completed.citations()));
        events.send(emitter, "token", completed.answer());
        events.send(
                emitter,
                "done",
                Map.of(
                        "messageId", completed.messageId(),
                        "intent", completed.intent(),
                        "citations", completed.citations(),
                        "replayed", true));
        emitter.complete();
    }

    private void replayCustomer(SseEmitter emitter, ChatModels.Message completed) {
        List<String> citations = citations(completed.metadataJson());
        events.send(emitter, "meta", Map.of("intent", completed.intent(), "degraded", false, "citations", citations));
        events.send(emitter, "token", completed.content());
        events.send(
                emitter,
                "done",
                Map.of(
                        "messageId",
                        completed.publicId(),
                        "intent",
                        completed.intent(),
                        "citations",
                        citations,
                        "replayed",
                        true));
        emitter.complete();
    }

    private void acquireGuestLock(String key) {
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", GUEST_REQUEST_LOCK_TTL))) {
            throw new ServiceFlowException(CONFLICT, ErrorCode.REQUEST_IN_PROGRESS, "请求正在处理中", true);
        }
    }

    private String requestKey(CurrentPrincipal principal, String sessionId, String clientRequestId) {
        return "chat:request:" + principal.subject() + ':' + sessionId + ':' + clientRequestId;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize chat metadata", exception);
        }
    }

    private List<String> citations(String metadata) {
        try {
            if (metadata == null || metadata.isBlank()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            objectMapper.readTree(metadata).path("citations").forEach(value -> values.add(value.asText()));
            return values;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<String> chunks(String value, int size) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return result;
        }
        for (int start = 0; start < value.length(); start += size) {
            result.add(value.substring(start, Math.min(value.length(), start + size)));
        }
        return result;
    }

    private ErrorCode errorCode(RuntimeException exception) {
        if (exception instanceof ServiceFlowException serviceFlowException) {
            return serviceFlowException.code();
        }
        if (exception instanceof ResponseStatusException status && "REQUEST_IN_PROGRESS".equals(status.getReason())) {
            return ErrorCode.REQUEST_IN_PROGRESS;
        }
        return ErrorCode.INTERNAL_ERROR;
    }
}
