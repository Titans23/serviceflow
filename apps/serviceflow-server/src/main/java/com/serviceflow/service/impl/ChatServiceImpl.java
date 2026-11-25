package com.serviceflow.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
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
    private static final Map<String, String> BUSINESS_EVENT_KEYS = Map.of(
            "order", "order",
            "order_selection_required", "orderCandidates",
            "product_selection_required", "productCandidates",
            "comparison_selection_required", "pendingComparison",
            "product_comparison", "productComparison",
            "action_required", "action",
            "ticket", "ticket");

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
            Map<String, Object> businessMetadata = new java.util.LinkedHashMap<>();
            List<WorkflowEvent> workflowEvents = new ArrayList<>();
            events.send(emitter, "progress", Map.of("message", "正在核对资料与业务状态"));
            CustomerWorkflow.Result result = workflow.execute(state, (name, data) -> {
                workflowEvents.add(new WorkflowEvent(name, data));
                if ("meta".equals(name)) businessMetadata.put("meta", data);
                String key = BUSINESS_EVENT_KEYS.get(name);
                if (key != null) businessMetadata.put(key, data);
                if ("product_selection_required".equals(name)
                        && data instanceof List<?> candidates
                        && !candidates.isEmpty()) {
                    businessMetadata.put("productQuestion", request.message());
                }
            });
            boolean usesModel = state.intent() == Intent.CHAT
                    || (result.groundingContext() != null
                            && !result.groundingContext().isBlank()
                            && ai.supportsGroundedGeneration());
            String answer;
            if (usesModel) {
                workflowEvents.forEach(event -> events.send(emitter, event.name(), event.data()));
                answer = generateModelAnswer(state, result, request.message(), emitter, startedNanos);
            } else {
                answer = result.answer();
            }
            String messageId = UUID.randomUUID().toString();
            businessMetadata.put("citations", result.citations());
            businessMetadata.put("intent", state.intent().name());
            businessMetadata.put("degraded", result.degraded());
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
            if (!usesModel) {
                // 确定性业务结果先提交，再写正文和卡片。断线后可按原请求重放已经提交的快照。
                workflowEvents.forEach(event -> events.send(emitter, event.name(), event.data()));
                StringBuilder sent = new StringBuilder();
                chunks(answer, ANSWER_CHUNK_SIZE).forEach(token -> appendToken(sent, emitter, token, startedNanos));
            }
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
     * 普通聊天直接转发模型文本，可信回答先解析事实选择，再发送确定性的正文片段。
     */
    private String generateModelAnswer(
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
        } else {
            // 商品查询存在可信上下文时，再让大模型基于这些事实生成自然语言回答。
            StringBuilder selection = new StringBuilder();
            ai.streamAnswer(
                    com.serviceflow.ai.GroundedAnswerComposer.SYSTEM_PROMPT,
                    productPrompt(state, userMessage, result.groundingContext()),
                    selection::append);
            String grounded = com.serviceflow.ai.GroundedAnswerComposer.compose(
                    result.groundingContext(), selection.toString(), objectMapper);
            chunks(grounded, ANSWER_CHUNK_SIZE).forEach(token -> appendToken(answer, emitter, token, startedNanos));
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
        replay(
                emitter,
                completed.messageId(),
                completed.intent(),
                completed.answer(),
                readMetadata(completed.metadataJson()),
                completed.citations(),
                completed.degraded());
    }

    private void replayCustomer(SseEmitter emitter, ChatModels.Message completed) {
        JsonNode metadata = readMetadata(completed.metadataJson());
        List<String> citations = new ArrayList<>();
        metadata.path("citations").forEach(value -> citations.add(value.asText()));
        replay(
                emitter,
                completed.publicId(),
                completed.intent(),
                completed.content(),
                metadata,
                citations,
                metadata.path("degraded").asBoolean());
    }

    private JsonNode readMetadata(String metadataJson) {
        try {
            if (metadataJson != null && !metadataJson.isBlank()) {
                JsonNode metadata = objectMapper.readTree(metadataJson);
                if (metadata != null && metadata.isObject()) return metadata;
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            // Historical metadata can be missing or invalid. The saved text remains replayable.
        }
        return objectMapper.createObjectNode();
    }

    private void replay(
            SseEmitter emitter,
            String messageId,
            String intent,
            String answer,
            JsonNode metadata,
            List<String> citations,
            boolean degraded) {
        events.send(
                emitter,
                "meta",
                metadata.path("meta").isObject()
                        ? metadata.get("meta")
                        : Map.of("intent", intent, "degraded", degraded, "citations", citations));
        events.send(emitter, "token", answer);
        BUSINESS_EVENT_KEYS.forEach((name, key) -> {
            if (metadata.hasNonNull(key)) events.send(emitter, name, metadata.get(key));
        });
        events.send(
                emitter,
                "done",
                Map.of("messageId", messageId, "intent", intent, "citations", citations, "replayed", true));
        emitter.complete();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize chat metadata", exception);
        }
    }

    private record WorkflowEvent(String name, Object data) {}

    private List<String> chunks(String value, int size) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return result;
        }
        for (int start = 0; start < value.length(); ) {
            int end = Math.min(value.length(), start + size);
            if (end < value.length() && Character.isSurrogatePair(value.charAt(end - 1), value.charAt(end))) end++;
            result.add(value.substring(start, end));
            start = end;
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
