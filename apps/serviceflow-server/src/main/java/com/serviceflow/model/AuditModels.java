package com.serviceflow.model;

import java.time.Instant;

public final class AuditModels {
    private AuditModels() {}

    public record AuditView(
            String publicId,
            String sessionPublicId,
            String customerName,
            String clientRequestId,
            String promptVersion,
            String modelName,
            String intent,
            String productIdsJson,
            String citationsJson,
            boolean degraded,
            long latencyMs,
            String resultStatus,
            String errorCode,
            String question,
            String answer,
            Instant createdAt) {}
}
