package com.serviceflow.model;

import java.time.Instant;
import java.util.List;

public final class TicketModels {
    private TicketModels() {}

    public record TicketView(String publicId, String subject, String description, String status, Instant createdAt) {}

    public record Message(String publicId, String authorRole, String content, Instant createdAt) {}

    public record Detail(TicketView ticket, List<Message> messages) {}

    public record RefundReview(
            String publicId, String status, String progress, String customerMessage, Instant updatedAt) {}

    public record AdminTicketView(
            String publicId,
            long customerId,
            String customerName,
            String sessionPublicId,
            String subject,
            String description,
            String status,
            String assignedTo,
            String resolutionNote,
            Instant createdAt,
            Instant updatedAt,
            String customerMessage) {}
}
