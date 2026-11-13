package com.serviceflow.model;

import java.math.BigDecimal;
import java.time.Instant;

public final class ReturnModels {
    private ReturnModels() {}

    public record Reference(String publicId, String orderNo, long orderId, long customerId, String status) {}

    public record View(
            String publicId,
            String orderNo,
            String productName,
            BigDecimal amount,
            String status,
            String reason,
            String reviewNote,
            String receiptNote,
            Instant createdAt,
            Instant reviewedAt,
            Instant receivedAt,
            Instant updatedAt,
            String refundStatus) {}
}
