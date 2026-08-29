package com.serviceflow.chat;

import static org.springframework.http.HttpStatus.CONFLICT;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.agent.CustomerWorkflow;
import com.serviceflow.agent.Intent;
import com.serviceflow.agent.ServiceFlowState;
import com.serviceflow.audit.AiAuditService;
import com.serviceflow.auth.CurrentPrincipal;
import com.serviceflow.config.ErrorCode;
import com.serviceflow.config.ServiceFlowException;
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
public final class ChatOrchestrator {
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

    public ChatOrchestrator(
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

    public void stream(
            CurrentPrincipal principal, String sessionId, ChatModels.MessageRequest request, SseEmitter emitter) {
        Long databaseId = principal.guest() ? null : sessions.requireCustomerSession(principal, sessionId);
        boolean guestLock = false;
        if (principal.guest()) {
            sessions.requireGuestSession(principal, sessionId);
            ChatMemory.CompletedResponse completed =
                    memory.completed(principal.subject(), sessionId, request.clientRequestId());
            if (completed != null) {
                replayGuest(emitter, completed);
                return;
            }
            acquireGuestLock(requestKey(principal, sessionId, request.clientRequestId()));
            guestLock = true;
            memory.append(principal.subject(), sessionId, "USER", request.message());
        } else {
            ChatRequestStore.BeginResult begin =
                    requests.begin(databaseId, request.clientRequestId(), request.message());
            if (!begin.started()) {
                replayCustomer(emitter, begin.completed());
                return;
            }
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
            CustomerWorkflow.Result result = workflow.execute(state, (name, data) -> events.send(emitter, name, data));
            if (state.productIds().size() == 1) {
                memory.setActiveProduct(
                        principal.subject(), sessionId, state.productIds().getFirst());
            }
            String answer = generateAnswer(state, result, request.message(), emitter);
            String messageId = UUID.randomUUID().toString();
            String metadata = writeJson(Map.of(
                    "citations", result.citations(), "intent", state.intent().name()));
            if (databaseId == null) {
                memory.storeCompleted(
                        principal.subject(),
                        sessionId,
                        request.clientRequestId(),
                        new ChatMemory.CompletedResponse(
                                messageId, answer, state.intent().name(), result.citations(), result.degraded()));
            } else {
                requests.complete(
                        databaseId,
                        request.clientRequestId(),
                        messageId,
                        answer,
                        state.intent().name(),
                        metadata);
            }
            memory.append(principal.subject(), sessionId, "ASSISTANT", answer);
            audit.success(
                    databaseId, state, request.message(), answer, result.citations(), result.degraded(), startedNanos);
            audited = true;
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
                redis.delete(requestKey(principal, sessionId, request.clientRequestId()));
            }
        }
    }

    private String generateAnswer(
            ServiceFlowState state, CustomerWorkflow.Result result, String userMessage, SseEmitter emitter) {
        StringBuilder answer = new StringBuilder();
        if (state.intent() == Intent.CHAT) {
            ai.streamAnswer(
                    "你是 ServiceFlow 客服，只回答售前商品和售后服务范围的问题。", userMessage, token -> appendToken(answer, emitter, token));
        } else if (result.groundingContext() != null
                && !result.groundingContext().isBlank()
                && ai.supportsGroundedGeneration()) {
            ai.streamAnswer(
                    PRODUCT_SYSTEM_PROMPT,
                    productPrompt(state, userMessage, result.groundingContext()),
                    token -> appendToken(answer, emitter, token));
        } else {
            chunks(result.answer(), ANSWER_CHUNK_SIZE).forEach(token -> appendToken(answer, emitter, token));
        }
        return answer.toString();
    }

    private void appendToken(StringBuilder answer, SseEmitter emitter, String token) {
        answer.append(token);
        events.send(emitter, "token", token);
    }

    private String productPrompt(ServiceFlowState state, String userMessage, String groundingContext) {
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
