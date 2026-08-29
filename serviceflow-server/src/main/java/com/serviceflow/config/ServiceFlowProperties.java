package com.serviceflow.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("serviceflow")
public record ServiceFlowProperties(Jwt jwt, Ai ai, String uploadDir, GuestRateLimit guestRateLimit, Rag rag) {
    public record Jwt(String secret, Duration customerTtl, Duration guestTtl) {}

    public record Ai(String mode, Chat chat, Embedding embedding, Reranker reranker) {
        public record Chat(String baseUrl, String apiKey, String model) {}

        public record Embedding(String baseUrl, String apiKey, String model) {}

        public record Reranker(String baseUrl, String apiKey, String model, String path) {}
    }

    public record GuestRateLimit(int maxRequests, Duration window) {}

    public record Rag(
            String mode,
            String milvusRestUrl,
            Duration connectTimeout,
            Duration readTimeout,
            String collectionName,
            int embeddingDimension) {}
}
