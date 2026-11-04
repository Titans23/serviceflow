package com.serviceflow.model;

import java.time.Instant;

public final class ChatActionModels {
    private ChatActionModels() {}

    public record Pending(
            String actionType, String subject, long customerId, String sessionId, String orderNo, String query) {}

    public record Action(
            String publicId,
            long customerId,
            String subject,
            String sessionPublicId,
            String sourceRequestId,
            String actionType,
            String orderNo,
            Integer orderVersion,
            String query,
            String status,
            String resultCode,
            String resultMessage,
            Instant expiresAt) {}

    public record Result(
            String actionStatus,
            Instant expiresAt,
            OrderModels.Operation operation,
            OrderModels.OrderView order,
            TicketModels.TicketView ticket) {}
}
