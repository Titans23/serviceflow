package com.serviceflow.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.config.ServiceFlowProperties;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OpenAiRerankerClient implements RerankerClient {

    private final ServiceFlowProperties.Ai.Reranker properties;
    private final RestClient client;
    private final ObjectMapper objectMapper;

    @Autowired
    public OpenAiRerankerClient(
            RestClient.Builder builder, ServiceFlowProperties properties, ObjectMapper objectMapper) {
        this(builder, properties, objectMapper, true);
    }

    OpenAiRerankerClient(
            RestClient.Builder builder,
            ServiceFlowProperties properties,
            ObjectMapper objectMapper,
            boolean configureTimeout) {
        this.properties = Objects.requireNonNull(properties.ai().reranker(), "Reranker configuration is required");
        this.objectMapper = Objects.requireNonNull(objectMapper);
        if (configureTimeout) {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(3_000);
            requestFactory.setReadTimeout(20_000);
            builder.requestFactory(requestFactory);
        }
        this.client = builder.baseUrl(this.properties.baseUrl()).build();
    }

    @Override
    @Retry(name = "reranker")
    @CircuitBreaker(name = "reranker")
    @Bulkhead(name = "reranker", type = Bulkhead.Type.SEMAPHORE)
    public List<String> rank(String query, List<Document> documents, int topN) {
        if (query == null || query.isBlank() || documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("Reranker query and documents are required");
        }
        int limit = Math.max(1, Math.min(topN, documents.size()));
        List<String> texts = documents.stream().map(Document::text).toList();
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", properties.model(),
                    "input", Map.of("query", query, "documents", texts),
                    "parameters", Map.of("top_n", limit, "return_documents", false)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize reranker request", exception);
        }
        String responseBody = client.post()
                .uri(properties.path())
                .headers(this::addAuthorization)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);
        JsonNode response;
        try {
            response = objectMapper.readTree(responseBody);
        } catch (Exception exception) {
            throw new IllegalStateException("Reranker endpoint returned invalid JSON", exception);
        }
        return parseRanking(response, documents, limit);
    }

    private void addAuthorization(HttpHeaders headers) {
        if (hasApiKey()) {
            headers.setBearerAuth(properties.apiKey());
        }
    }

    private boolean hasApiKey() {
        return properties.apiKey() != null
                && !properties.apiKey().isBlank()
                && !"disabled".equalsIgnoreCase(properties.apiKey());
    }

    private List<String> parseRanking(JsonNode response, List<Document> documents, int limit) {
        JsonNode rows = response == null ? null : response.path("output").path("results");
        if (rows == null || !rows.isArray()) {
            rows = response == null ? null : response.path("results");
        }
        if (rows == null || !rows.isArray()) {
            throw new IllegalStateException("Reranker endpoint returned no results");
        }

        List<RankedDocument> ranked = new ArrayList<>();
        for (JsonNode row : rows) {
            int index = row.path("index").asInt(-1);
            if (index >= 0 && index < documents.size()) {
                ranked.add(new RankedDocument(
                        documents.get(index).id(), row.path("relevance_score").asDouble(0D)));
            } else if (row.hasNonNull("id")) {
                ranked.add(new RankedDocument(
                        row.path("id").asText(), row.path("relevance_score").asDouble(0D)));
            }
        }
        ranked.sort(Comparator.comparingDouble(RankedDocument::score).reversed());

        Set<String> seen = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (RankedDocument document : ranked) {
            if (seen.add(document.id())) {
                result.add(document.id());
            }
            if (result.size() == limit) {
                break;
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("Reranker endpoint returned no valid document indexes");
        }
        return result;
    }

    private record RankedDocument(String id, double score) {}
}
