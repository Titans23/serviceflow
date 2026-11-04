package com.serviceflow.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.model.RefundDeliveryModels;
import com.serviceflow.model.RefundModels;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "serviceflow.refund.dispatch-enabled", havingValue = "true")
public class RefundChannelClient {
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json;
    private final String baseUrl;
    private final String token;

    public RefundChannelClient(
            ObjectMapper json,
            @Value("${serviceflow.refund.channel-url}") String baseUrl,
            @Value("${serviceflow.refund.channel-token}") String token) {
        if (token.isBlank()) throw new IllegalArgumentException("Refund channel token is required");
        this.json = json;
        this.baseUrl = baseUrl;
        this.token = token;
    }

    public RefundDeliveryModels.ChannelResult query(String id) {
        return request("/refunds/" + id, null);
    }

    public RefundDeliveryModels.ChannelResult submit(RefundModels.Refund refund) {
        return request(
                "/refunds",
                Map.of("refundId", refund.publicId(), "attemptNo", refund.attemptNo(), "amount", refund.amount()));
    }

    private RefundDeliveryModels.ChannelResult request(String path, Object body) {
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(4))
                    .header("X-Refund-Token", token);
            if (body == null) request.GET();
            else
                request.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (body == null && response.statusCode() == 404) return null;
            if (response.statusCode() != 200) throw new IllegalStateException("Channel HTTP " + response.statusCode());
            return json.readValue(response.body(), RefundDeliveryModels.ChannelResult.class);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Channel request interrupted", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Channel transport unavailable", exception);
        }
    }
}
