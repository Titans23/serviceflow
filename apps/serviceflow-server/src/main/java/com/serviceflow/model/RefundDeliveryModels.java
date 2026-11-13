package com.serviceflow.model;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

public final class RefundDeliveryModels {
    private RefundDeliveryModels() {}

    public static String reviewActionId(String refundId) {
        return UUID.nameUUIDFromBytes(("refund-review:" + refundId).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    public record Task(
            long refundId,
            int attemptNo,
            String state,
            String leaseToken,
            Instant leaseUntil,
            Instant nextRunAt,
            Instant deadlineAt,
            int queryCount,
            int submitCount,
            String lastError,
            String ticketId) {}

    public record Claim(Task task, RefundModels.Refund refund) {}

    public record ChannelResult(String refundId, int attemptNo, String status, BigDecimal amount, String reason) {}

    public record Owner(long customerId, String sessionId) {}
}
