package com.serviceflow.service.impl;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public final class ChatServiceImpl implements ChatService {
    private static final int ANSWER_CHUNK_SIZE = 28;

    private final ChatMemory memory;
    private final ChatSessionService sessions;
    private final ChatRequestStore requests;
    private final CustomerWorkflow workflow;
    private final AiGateway ai;
    private final ObjectMapper objectMapper;
    private final AiAuditService audit;
    private final SseEventWriter events;

    public ChatServiceImpl(
            ChatMemory memory,
            ChatSessionService sessions,
            ChatRequestStore requests,
            CustomerWorkflow workflow,
            AiGateway ai,
            ObjectMapper objectMapper,
            AiAuditService audit,
            SseEventWriter events) {
        this.memory = memory;
        this.sessions = sessions;
        this.requests = requests;
        this.workflow = workflow;
        this.ai = ai;
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

        String subject = principal.subject();
        String requestId = request.clientRequestId();
        String executionId;
        boolean firstCustomerExecution = false;
        if (principal.guest()) {
            // 校验当前访客只能访问自己的 Redis 会话。
            sessions.requireGuestSession(principal, sessionId);

            executionId = UUID.randomUUID().toString();
            var claim = memory.claimGuestRequest(subject, sessionId, requestId, executionId);
            if (!claim.started()) {
                if (claim.completed() == null) throw ChatRequestStore.inProgress();
                replayGuest(emitter, claim.completed());
                return;
            }
        } else {
            // 在同一数据库事务中登记请求并保存用户消息；started=false 表示已有结果可重放。
            ChatRequestStore.BeginResult begin = requests.begin(databaseId, requestId, request.message());
            if (!begin.started()) {
                replayCustomer(emitter, begin.completed());
                return;
            }
            executionId = begin.executionId();
            firstCustomerExecution = begin.firstExecution();
        }

        long startedNanos = System.nanoTime();
        ServiceFlowState state = null;
        boolean committed = false;
        try {
            if (firstCustomerExecution) memory.append(subject, sessionId, "USER", request.message());
            Long contextProduct =
                    request.pageContext() != null && request.pageContext().productId() != null
                            ? request.pageContext().productId()
                            : memory.activeProduct(principal.subject(), sessionId);
            state = new ServiceFlowState(
                    sessionId, principal, request.message(), contextProduct, request.clientRequestId());
            state.selectedProductId(request.selectedProductId());
            state.activeOrderNo(
                    request.pageContext() == null ? null : request.pageContext().orderNo());
            state.orderSelection(request.orderSelection());
            state.comparisonSelection(request.comparisonSelection());
            // 暂时把工作流视为黑盒：输入本次请求状态，输出业务结果，并把过程中产生的事件转发给前端。
            Map<String, Object> businessMetadata = new java.util.LinkedHashMap<>();
            events.send(emitter, "progress", Map.of("message", "正在核对资料与业务状态"));
            CustomerWorkflow.Result result = workflow.execute(state, (name, data) -> {
                if ("order".equals(name)) businessMetadata.put("order", data);
                if ("order_selection_required".equals(name)) businessMetadata.put("orderCandidates", data);
                // 结构化比较数据随回复保存，历史与重放可以恢复当时的表格和待确认清单。
                if ("comparison_selection_required".equals(name)) businessMetadata.put("pendingComparison", data);
                if ("product_comparison".equals(name)) businessMetadata.put("productComparison", data);
                if ("product_selection_required".equals(name)
                        && data instanceof List<?> candidates
                        && !candidates.isEmpty()) {
                    // 保存发起澄清的原问题，刷新或重放时能按具体规格继续回答这条问题。
                    businessMetadata.put("productCandidates", candidates);
                    businessMetadata.put("productQuestion", request.message());
                }
                if ("action_required".equals(name)) businessMetadata.put("action", data);
                events.send(emitter, name, data);
            });
            // 生成最终回答；生成期间每个文本片段已经通过 token 事件实时发送给前端。
            String answer = generateAnswer(state, result, request.message(), emitter, startedNanos);
            String messageId = UUID.randomUUID().toString();
            businessMetadata.put("citations", result.citations());
            businessMetadata.put("intent", state.intent().name());
            String metadata = writeJson(businessMetadata);
            if (databaseId == null) {
                if (!memory.completeGuestRequest(
                        subject,
                        sessionId,
                        requestId,
                        executionId,
                        request.message(),
                        new ChatMemory.CompletedResponse(
                                messageId,
                                answer,
                                state.intent().name(),
                                result.citations(),
                                result.degraded(),
                                metadata))) throw ChatRequestStore.inProgress();
            } else {
                // 登录用户将助手消息写入 MySQL，并把 chat_request 状态改为 COMPLETED。
                requests.complete(
                        databaseId,
                        requestId,
                        executionId,
                        messageId,
                        answer,
                        state.intent().name(),
                        metadata);
            }
            committed = true;
            if (databaseId != null) memory.append(subject, sessionId, "ASSISTANT", answer);
            // 只有提交成功的执行可以发布新的单品上下文。
            if (state.productIds().size() == 1) {
                memory.setActiveProduct(subject, sessionId, state.productIds().getFirst());
            } else if (contextProduct != null && state.clearProductContext()) {
                memory.clearActiveProduct(subject, sessionId);
            }
            audit.success(
                    databaseId, state, request.message(), answer, result.citations(), result.degraded(), startedNanos);
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
            if (!committed) {
                if (databaseId != null)
                    requests.fail(
                            databaseId,
                            requestId,
                            executionId,
                            errorCode(exception).name());
                audit.failure(databaseId, requestId, state, request.message(), exception, startedNanos);
            }
            throw exception;
        } finally {
            if (principal.guest() && !committed) {
                memory.releaseGuestRequest(subject, sessionId, requestId, executionId);
            }
        }
    }

    /**
     * 根据意图选择回答来源，并把所有来源统一转换成逐片段输出。
     *
     * <p>普通聊天和有可信上下文的回答来自 AI 流；已有的非流式结果则由后端主动切片。
     */
    private String generateAnswer(
            ServiceFlowState state,
            CustomerWorkflow.Result result,
            String userMessage,
            SseEmitter emitter,
            long startedNanos) {
        StringBuilder answer = new StringBuilder();
        if (state.intent() == Intent.CHAT) {
            ai.streamAnswer(
                    "你是 ServiceFlow 客服，只回答售前商品和售后服务范围的问题。",
                    userMessage,
                    token -> appendToken(answer, emitter, token, startedNanos));
        } else if (result.groundingContext() != null
                && !result.groundingContext().isBlank()
                && ai.supportsGroundedGeneration()) {
            // 商品查询存在可信上下文时，再让大模型基于这些事实生成自然语言回答。
            StringBuilder selection = new StringBuilder();
            ai.streamAnswer(
                    com.serviceflow.ai.GroundedAnswerComposer.SYSTEM_PROMPT,
                    productPrompt(state, userMessage, result.groundingContext()),
                    selection::append);
            String grounded = com.serviceflow.ai.GroundedAnswerComposer.compose(
                    result.groundingContext(), selection.toString(), objectMapper);
            chunks(grounded, ANSWER_CHUNK_SIZE).forEach(token -> appendToken(answer, emitter, token, startedNanos));
        } else {
            // 即使上游只有完整答案，也按固定长度切片，让前端保持相同的 SSE 消费方式。
            chunks(result.answer(), ANSWER_CHUNK_SIZE)
                    .forEach(token -> appendToken(answer, emitter, token, startedNanos));
        }
        return answer.toString();
    }

    /** 同时累积最终完整答案，并把当前文本片段作为 token 事件推送给浏览器。 */
    private void appendToken(StringBuilder answer, SseEmitter emitter, String token, long startedNanos) {
        if (answer.isEmpty() && !token.isEmpty()) audit.firstToken(startedNanos);
        answer.append(token);
        events.send(emitter, "token", token);
    }

    private String productPrompt(ServiceFlowState state, String userMessage, String groundingContext) {
        // 最终用户提示词由最近对话、本轮问题和可信检索依据三部分组成。
        List<Map<String, String>> history = memory.history(state.principal().subject(), state.sessionId());
        return com.serviceflow.ai.GroundedAnswerComposer.prompt(history, userMessage, groundingContext);
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
        replayProductEvents(emitter, completed.metadataJson());
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
        try {
            var metadata = objectMapper.readTree(completed.metadataJson() == null ? "{}" : completed.metadataJson());
            sendProductEvents(emitter, metadata);
            if (metadata.hasNonNull("order")) events.send(emitter, "order", metadata.get("order"));
            if (metadata.hasNonNull("orderCandidates"))
                events.send(emitter, "order_selection_required", metadata.get("orderCandidates"));
            if (metadata.hasNonNull("action")) events.send(emitter, "action_required", metadata.get("action"));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            // Historical messages may not contain valid business metadata; their saved text remains replayable.
        }
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

    private void replayProductEvents(SseEmitter emitter, String metadataJson) {
        if (metadataJson == null) return;
        try {
            var metadata = objectMapper.readTree(metadataJson);
            sendProductEvents(emitter, metadata);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            // Historical guest responses may lack structured metadata.
        }
    }

    private void sendProductEvents(SseEmitter emitter, com.fasterxml.jackson.databind.JsonNode metadata) {
        if (metadata == null) return;
        if (metadata.hasNonNull("productCandidates"))
            events.send(emitter, "product_selection_required", metadata.get("productCandidates"));
        if (metadata.hasNonNull("productComparison"))
            events.send(emitter, "product_comparison", metadata.get("productComparison"));
        if (metadata.hasNonNull("pendingComparison"))
            events.send(emitter, "comparison_selection_required", metadata.get("pendingComparison"));
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
