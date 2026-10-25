package com.serviceflow.rag;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.serviceflow.ai.AiGateway;
import com.serviceflow.config.ServiceFlowProperties;
import com.serviceflow.mapper.KnowledgeMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MilvusCollectionInitializationTest {
    private WireMockServer http;
    private MilvusRagService service;
    private EmbeddingClient embedding;

    @BeforeEach
    void setUp() {
        http = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        http.start();
        embedding = mock(EmbeddingClient.class);
        var properties = new ServiceFlowProperties(
                null,
                null,
                null,
                null,
                new ServiceFlowProperties.Rag(
                        "cloud",
                        http.baseUrl(),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        "serviceflow_chunks",
                        1024));
        service = new MilvusRagService(
                properties,
                embedding,
                mock(RerankerClient.class),
                mock(KnowledgeMapper.class),
                mock(AiGateway.class),
                new SimpleMeterRegistry(),
                new RagTraceRecorder(false, "", new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    @AfterEach
    void close() {
        http.stop();
    }

    @Test
    void firstVersionCleanupCreatesMissingCollectionBeforeDeletion() {
        http.stubFor(post(urlEqualTo("/v2/vectordb/collections/has"))
                .willReturn(okJson("{\"code\":0,\"data\":{\"has\":false}}")));
        http.stubFor(post(urlEqualTo("/v2/vectordb/collections/create"))
                .inScenario("initialization")
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("ready")
                .willReturn(okJson("{\"code\":0}")));
        http.stubFor(post(urlEqualTo("/v2/vectordb/entities/delete"))
                .inScenario("initialization")
                .whenScenarioStateIs("ready")
                .willReturn(okJson("{\"code\":0}")));

        service.deleteVersion(7);
        service.deleteVersion(8);

        http.verify(1, postRequestedFor(urlEqualTo("/v2/vectordb/collections/create")));
        http.verify(
                1,
                postRequestedFor(urlEqualTo("/v2/vectordb/entities/delete"))
                        .withRequestBody(matchingJsonPath("$.filter", equalTo("documentVersionId == 7"))));
        http.verify(
                1,
                postRequestedFor(urlEqualTo("/v2/vectordb/entities/delete"))
                        .withRequestBody(matchingJsonPath("$.filter", equalTo("documentVersionId == 8"))));
        verifyNoInteractions(embedding);
    }

    @Test
    void existingCollectionDoesNotGetRecreated() {
        http.stubFor(post(urlEqualTo("/v2/vectordb/collections/has"))
                .willReturn(okJson("{\"code\":0,\"data\":{\"has\":true}}")));
        http.stubFor(post(urlEqualTo("/v2/vectordb/entities/delete")).willReturn(okJson("{\"code\":0}")));
        service.deleteVersion(9);
        http.verify(0, postRequestedFor(urlEqualTo("/v2/vectordb/collections/create")));
    }

    @Test
    void failedInitializationMustNotDeleteOrMarkCollectionReady() {
        http.stubFor(post(urlEqualTo("/v2/vectordb/collections/has"))
                .willReturn(okJson("{\"code\":0,\"data\":{\"has\":false}}")));
        http.stubFor(post(urlEqualTo("/v2/vectordb/collections/create"))
                .willReturn(okJson("{\"code\":65535,\"message\":\"test failure\"}")));
        assertThatThrownBy(() -> service.deleteVersion(10)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.deleteVersion(10)).isInstanceOf(IllegalStateException.class);
        http.verify(2, postRequestedFor(urlEqualTo("/v2/vectordb/collections/create")));
        http.verify(0, postRequestedFor(urlEqualTo("/v2/vectordb/entities/delete")));
    }
}
