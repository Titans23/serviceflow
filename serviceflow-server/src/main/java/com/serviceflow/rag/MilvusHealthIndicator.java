package com.serviceflow.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.config.ServiceFlowProperties;
import java.time.Duration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component("milvus")
public class MilvusHealthIndicator implements HealthIndicator {
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public MilvusHealthIndicator(
            RestClient.Builder builder, ServiceFlowProperties properties, ObjectMapper objectMapper) {
        this.enabled = "cloud".equalsIgnoreCase(properties.rag().mode());
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(1));
        this.client = builder.requestFactory(factory)
                .baseUrl(properties.rag().milvusRestUrl())
                .build();
    }

    @Override
    public Health health() {
        if (!enabled) {
            return Health.up().withDetail("mode", "disabled").build();
        }
        try {
            String responseBody = client.post()
                    .uri("/v2/vectordb/collections/list")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"dbName\":\"default\"}")
                    .retrieve()
                    .body(String.class);
            JsonNode response = objectMapper.readTree(responseBody);
            if (response != null && response.path("code").asInt(-1) == 0) {
                return Health.up().build();
            }
            return Health.down()
                    .withDetail("reason", "Milvus REST API returned a non-zero code")
                    .build();
        } catch (Exception exception) {
            return Health.down().withException(exception).build();
        }
    }
}
