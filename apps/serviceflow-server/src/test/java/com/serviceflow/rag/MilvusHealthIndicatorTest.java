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
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.web.client.RestClient;

class MilvusHealthIndicatorTest {
    private WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        com.github.tomakehurst.wiremock.client.WireMock.configureFor("localhost", wireMock.port());
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void reportsUpWhenMilvusRestApiAcceptsCollectionListRequest() {
        stubFor(post(urlEqualTo("/v2/vectordb/collections/list"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":0,\"data\":[\"serviceflow_chunks\"]}")));

        var indicator = new MilvusHealthIndicator(RestClient.builder(), properties("cloud"), new ObjectMapper());

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void doesNotCallMilvusWhenRagModeIsDisabled() {
        var indicator = new MilvusHealthIndicator(RestClient.builder(), properties("demo"), new ObjectMapper());

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails()).containsEntry("mode", "disabled");
    }

    private ServiceFlowProperties properties(String mode) {
        return new ServiceFlowProperties(
                null,
                null,
                null,
                null,
                new ServiceFlowProperties.Rag(
                        mode,
                        "http://localhost:" + wireMock.port(),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        "serviceflow_chunks",
                        1024));
    }
}
