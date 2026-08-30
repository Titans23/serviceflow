package com.serviceflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.agent.ServiceFlowState;
import com.serviceflow.config.ServiceFlowProperties;
import com.serviceflow.mapper.AiAuditMapper;
import com.serviceflow.model.AuditModels;
import com.serviceflow.service.AiAuditService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class AiAuditServiceImpl implements AiAuditService {

    public static final String PROMPT_VERSION = "customer-v1";
    private final AiAuditMapper mapper;
    private final ObjectMapper objectMapper;
    private final MeterRegistry metrics;
    private final String modelName;

    public AiAuditServiceImpl(
            AiAuditMapper mapper, ObjectMapper objectMapper, MeterRegistry metrics, ServiceFlowProperties properties) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.modelName = properties.ai().chat().model();
    }

    @Override
    public void success(
            Long sessionId,
            ServiceFlowState state,
            String question,
            String answer,
            List<String> citations,
            boolean degraded,
            long startedNanos) {
        long latencyMs = elapsedMillis(startedNanos);
        String intent = state.intent().name();
        if (sessionId != null) {
            mapper.insert(
                    UUID.randomUUID().toString(),
                    sessionId,
                    state.clientRequestId(),
                    PROMPT_VERSION,
                    modelName,
                    intent,
                    json(state.productIds()),
                    json(citations),
                    degraded,
                    latencyMs,
                    "SUCCESS",
                    null,
                    question,
                    answer);
        }
        recordMetrics(intent, "SUCCESS", degraded, latencyMs);
    }

    @Override
    public void failure(
            Long sessionId,
            String clientRequestId,
            ServiceFlowState state,
            String question,
            RuntimeException exception,
            long startedNanos) {
        long latencyMs = elapsedMillis(startedNanos);
        String intent = state == null || state.intent() == null
                ? "UNKNOWN"
                : state.intent().name();
        if (sessionId != null) {
            mapper.insert(
                    UUID.randomUUID().toString(),
                    sessionId,
                    clientRequestId,
                    PROMPT_VERSION,
                    modelName,
                    intent,
                    json(state == null ? List.of() : state.productIds()),
                    "[]",
                    false,
                    latencyMs,
                    "FAILED",
                    exception.getClass().getSimpleName(),
                    question,
                    null);
        }
        recordMetrics(intent, "FAILED", false, latencyMs);
    }

    @Override
    public List<AuditModels.AuditView> recent(int limit) {
        return mapper.findRecent(Math.max(1, Math.min(limit, 200)));
    }

    private void recordMetrics(String intent, String status, boolean degraded, long latencyMs) {
        metrics.counter("serviceflow.chat.requests", "intent", intent, "status", status)
                .increment();
        if (degraded) {
            metrics.counter("serviceflow.chat.degraded", "intent", intent).increment();
        }
        metrics.timer("serviceflow.chat.duration", "intent", intent, "status", status)
                .record(Duration.ofMillis(latencyMs));
    }

    private long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize AI audit", exception);
        }
    }
}
