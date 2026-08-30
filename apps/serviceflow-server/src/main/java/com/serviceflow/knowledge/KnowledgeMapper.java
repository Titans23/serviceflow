package com.serviceflow.knowledge;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeMapper {
    int insertDocument(
            @Param("publicId") String publicId,
            @Param("title") String title,
            @Param("documentType") String documentType,
            @Param("productId") Long productId);

    DocumentRow findDocument(@Param("publicId") String publicId);

    int nextVersion(@Param("documentId") long documentId);

    int insertVersion(
            @Param("documentId") long documentId,
            @Param("versionNo") int versionNo,
            @Param("sourceName") String sourceName,
            @Param("storagePath") String storagePath);

    VersionRow findVersion(@Param("documentId") long documentId, @Param("versionNo") int versionNo);

    VersionRow findVersionById(@Param("id") long id);

    List<DocumentView> list();

    int markProcessing(@Param("id") long id);

    int markFailed(@Param("id") long id, @Param("message") String message);

    int markPending(@Param("id") long id);

    int markReady(@Param("id") long id);

    int activate(@Param("documentId") long documentId, @Param("versionId") long versionId);

    List<Long> activeVersionIds(@Param("documentType") String documentType, @Param("productIds") List<Long> productIds);

    int enqueueOutbox(@Param("versionId") long versionId, @Param("payloadJson") String payloadJson);

    int requeueOutbox(@Param("versionId") long versionId, @Param("payloadJson") String payloadJson);

    List<OutboxRow> dueOutbox(@Param("limit") int limit);

    int markOutboxPublished(@Param("id") long id);

    int markOutboxFailed(
            @Param("id") long id, @Param("message") String message, @Param("nextAttemptAt") Instant nextAttemptAt);

    long pendingOutboxCount();

    record DocumentRow(
            long id, String publicId, String title, String documentType, Long productId, Long activeVersionId) {}

    record VersionRow(
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

    record DocumentView(
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

    record OutboxRow(long id, long documentVersionId, String payloadJson, int attempts, Instant createdAt) {}
}
