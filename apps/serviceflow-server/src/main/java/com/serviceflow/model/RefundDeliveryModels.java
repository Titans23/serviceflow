package com.serviceflow.model;

import java.math.BigDecimal;
import java.time.Instant;

public final class RefundDeliveryModels {
    private RefundDeliveryModels() {}

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
