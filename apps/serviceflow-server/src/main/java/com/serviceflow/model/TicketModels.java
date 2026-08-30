package com.serviceflow.model;

import java.time.Instant;

public final class TicketModels {
    private TicketModels() {}

    public record TicketView(String publicId, String subject, String description, String status, Instant createdAt) {}

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
            Instant updatedAt) {}
}
