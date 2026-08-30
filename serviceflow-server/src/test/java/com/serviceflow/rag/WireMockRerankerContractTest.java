package com.serviceflow.rag;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.serviceflow.config.ServiceFlowProperties;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class WireMockRerankerContractTest {
    private WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        com.github.tomakehurst.wiremock.client.WireMock.configureFor("localhost", wireMock.port());
        stubFor(post(urlEqualTo("/services/rerank"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"output\":{\"results\":[{\"index\":0,\"relevance_score\":0.9}]}}")));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void verifiesRerankerHttpContractAgainstWireMock() {
        var properties = new ServiceFlowProperties(
                null,
                new ServiceFlowProperties.Ai(
                        "cloud",
                        new ServiceFlowProperties.Ai.Chat("http://localhost", "disabled", "chat"),
                        new ServiceFlowProperties.Ai.Embedding("http://localhost", "disabled", "embedding"),
                        new ServiceFlowProperties.Ai.Reranker(
                                "http://localhost:" + wireMock.port(), "disabled", "qwen3-rerank", "/services/rerank")),
                null,
                null,
                null);
        var builder = RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory());
        var client = new OpenAiRerankerClient(builder, properties, new ObjectMapper(), false);

        assertThat(client.rank("续航", List.of(new RerankerClient.Document("chunk-1", "电池续航")), 1))
                .containsExactly("chunk-1");
    }
}
