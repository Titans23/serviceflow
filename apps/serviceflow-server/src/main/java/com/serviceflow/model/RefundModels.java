package com.serviceflow.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class RefundModels {
    private RefundModels() {}

    public record Refund(
            long id,
            String publicId,
            long orderId,
            String orderNo,
            BigDecimal amount,
            String status,
            int attemptNo,
            String failureReason,
            Instant createdAt,
            Instant updatedAt) {
        public RefundView view() {
            return new RefundView(publicId, orderNo, amount, status, attemptNo, failureReason, createdAt, updatedAt);
        }
    }

    public record RefundView(
            String publicId,
            String orderNo,
            BigDecimal amount,
            String status,
            int attemptNo,
            String failureReason,
            Instant createdAt,
            Instant updatedAt) {}

    public record Event(
            String eventId,
            long refundId,
            int attemptNo,
            String outcome,
            BigDecimal amount,
            String reason,
            String disposition) {}

    public record SandboxEvent(
            @NotNull @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                    String eventId,
            @Min(1) int attemptNo,
            @NotNull @Pattern(regexp = "SUCCEEDED|FAILED") String outcome,
            @NotNull @DecimalMin("0.00") BigDecimal amount,
            @Size(max = 500) String reason) {}

    public record RetryRequest(@Min(1) int expectedAttemptNo) {}

    public record EventResult(RefundView refund, boolean replayed, String disposition) {}
}
