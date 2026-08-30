package com.serviceflow.model;

import java.time.Instant;

public final class KnowledgeModels {
    private KnowledgeModels() {}

    public record Document(
            long id, String publicId, String title, String documentType, Long productId, Long activeVersionId) {}

    public record Version(
            long id,
            long documentId,
            int versionNo,
            String sourceName,
            String storagePath,
            String status,
            String errorMessage,
            String title,
            String documentType,
            Long productId) {}

    public record DocumentView(
            String publicId,
            String title,
            String documentType,
            Long productId,
            Long activeVersionId,
            Long latestVersionId,
            Integer latestVersion,
            String latestStatus,
            String errorMessage,
            Instant createdAt) {}

    public record OutboxEvent(long id, long documentVersionId, String payloadJson, int attempts, Instant createdAt) {}
}
