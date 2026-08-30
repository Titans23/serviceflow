package com.serviceflow.rag;

import com.serviceflow.config.ServiceFlowProperties;
import java.time.Duration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component("milvus")
public class MilvusHealthIndicator implements HealthIndicator {
    private final RestClient client;
    private final boolean enabled;

    public MilvusHealthIndicator(RestClient.Builder builder, ServiceFlowProperties properties) {
        this.enabled = "cloud".equalsIgnoreCase(properties.rag().mode());
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
            client.get().uri("/healthz").retrieve().toBodilessEntity();
            return Health.up().build();
        } catch (Exception exception) {
            return Health.down().withException(exception).build();
        }
    }
}
