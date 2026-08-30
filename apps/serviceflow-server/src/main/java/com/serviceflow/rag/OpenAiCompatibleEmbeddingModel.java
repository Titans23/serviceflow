package com.serviceflow.rag;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.config.ServiceFlowProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Primary
@Component
@ConditionalOnProperty(name = "serviceflow.rag.mode", havingValue = "cloud")
public class OpenAiCompatibleEmbeddingModel implements EmbeddingModel {

    private final RestClient client;
    private final String model;
    private final ObjectMapper objectMapper;

    @Autowired
    public OpenAiCompatibleEmbeddingModel(
            RestClient.Builder builder, ServiceFlowProperties properties, ObjectMapper objectMapper) {
        this(
                builder,
                properties.ai().embedding().baseUrl(),
                properties.ai().embedding().apiKey(),
                properties.ai().embedding().model(),
                objectMapper);
    }

    OpenAiCompatibleEmbeddingModel(
            RestClient.Builder builder, String baseUrl, String apiKey, String model, ObjectMapper objectMapper) {
        this.client = builder.baseUrl(Objects.requireNonNull(baseUrl))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + Objects.requireNonNull(apiKey))
                .build();
        this.model = Objects.requireNonNull(model);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> inputs = request.getInstructions();
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("Embedding inputs must not be empty");
        }
        String requestedModel =
                request.getOptions() != null && request.getOptions().getModel() != null
                        ? request.getOptions().getModel()
                        : model;
        String responseBody = client.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Request(inputs, requestedModel, "float"))
                .retrieve()
                .body(String.class);
        JsonNode response;
        try {
            response = objectMapper.readTree(responseBody);
        } catch (Exception exception) {
            throw new IllegalStateException("Embedding provider returned invalid JSON", exception);
        }
        JsonNode data = response == null ? null : response.path("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            throw new IllegalStateException("Embedding provider returned no vectors");
        }
        List<Embedding> embeddings = new ArrayList<>();
        for (JsonNode item : data) {
            JsonNode vector = item.path("embedding");
            if (!vector.isArray() || vector.isEmpty()) {
                throw new IllegalStateException("Embedding provider returned an invalid vector");
            }
            float[] values = new float[vector.size()];
            for (int index = 0; index < vector.size(); index++) {
                values[index] = (float) vector.get(index).asDouble();
            }
            embeddings.add(new Embedding(values, item.path("index").asInt()));
        }
        embeddings.sort(Comparator.comparingInt(Embedding::getIndex));
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(getEmbeddingContent(document));
    }

    private record Request(List<String> input, String model, @JsonProperty("encoding_format") String encodingFormat) {}
}
