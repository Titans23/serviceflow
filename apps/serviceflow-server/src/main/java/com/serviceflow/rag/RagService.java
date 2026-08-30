package com.serviceflow.rag;

import java.util.List;

public interface RagService {
    SearchResult search(String query, String documentType, List<Long> productIds);

    void ingest(Chunk chunk);

    void deleteVersion(long documentVersionId);

    record Chunk(
            String chunkId,
            long documentId,
            long documentVersionId,
            String documentType,
            Long productId,
            String title,
            String category,
            String source,
            String content) {}

    record Evidence(String chunkId, String title, String source, String content, double score) {}

    record SearchResult(List<Evidence> evidence, boolean sufficient, boolean degraded) {}
}
