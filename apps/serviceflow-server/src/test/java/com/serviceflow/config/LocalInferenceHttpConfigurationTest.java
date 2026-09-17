package com.serviceflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.web.reactive.function.client.WebClient;

class LocalInferenceHttpConfigurationTest {
    private final ApplicationContextRunner context =
            new ApplicationContextRunner().withUserConfiguration(LocalInferenceHttpConfiguration.class);

    @Test
    void keepsDefaultTransportUnlessExplicitlyEnabled() {
        context.run(application -> assertThat(application).doesNotHaveBean(WebClientCustomizer.class));
    }

    @Test
    void streamsOverHttp1WithoutSendingAnH2cUpgrade() throws Exception {
        AtomicReference<String> upgrade = new AtomicReference<>();
        AtomicReference<String> protocol = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stream", exchange -> {
            upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            protocol.set(exchange.getProtocol());
            exchange.getRequestBody().readAllBytes();
            byte[] body = "data: ready\n\n".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            context.withPropertyValues("serviceflow.ai.http1-enabled=true").run(application -> {
                WebClient.Builder builder = WebClient.builder();
                application.getBean(WebClientCustomizer.class).customize(builder);
                String result = builder.build()
                        .post()
                        .uri("http://127.0.0.1:" + server.getAddress().getPort() + "/stream")
                        .bodyValue("{}")
                        .retrieve()
                        .bodyToFlux(String.class)
                        .blockLast(Duration.ofSeconds(5));
                assertThat(result).isEqualTo("ready");
                assertThat(protocol.get()).isEqualTo("HTTP/1.1");
                assertThat(upgrade.get()).isNull();
            });
        } finally {
            server.stop(0);
        }
    }
}
