package com.serviceflow.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.JdkClientHttpConnector;

/** Optional transport compatibility for local inference servers without h2c support. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "serviceflow.ai.http1-enabled", havingValue = "true")
public class LocalInferenceHttpConfiguration {
    @Bean
    WebClientCustomizer localInferenceHttp1Customizer() {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        return builder -> builder.clientConnector(new JdkClientHttpConnector(client));
    }
}
