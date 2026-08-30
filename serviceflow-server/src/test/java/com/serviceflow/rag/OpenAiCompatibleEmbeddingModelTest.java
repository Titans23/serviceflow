package com.serviceflow.rag;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleEmbeddingModelTest {
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
    void mapsOpenAiCompatibleEmbeddingResponseInIndexOrder() {
        stubFor(
                post(urlEqualTo("/embeddings"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                {"object":"list","data":[
                                  {"object":"embedding","index":1,"embedding":[0.3,0.4]},
                                  {"object":"embedding","index":0,"embedding":[0.1,0.2]}],
                                 "model":"text-embedding-v4","usage":{"prompt_tokens":2,"total_tokens":2}}
                                """)));
        var model = new OpenAiCompatibleEmbeddingModel(
                RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory()),
                "http://localhost:" + wireMock.port(),
                "test-key",
                "text-embedding-v4",
                new ObjectMapper());

        var result = model.call(new EmbeddingRequest(List.of("one", "two"), null));

        assertThat(result.getResults()).hasSize(2);
        assertThat(result.getResults().getFirst().getOutput()).containsExactly(0.1f, 0.2f);
        verify(
                postRequestedFor(urlEqualTo("/embeddings"))
                        .withRequestBody(
                                equalToJson(
                                        "{\"input\":[\"one\",\"two\"],\"model\":\"text-embedding-v4\",\"encoding_format\":\"float\"}")));
    }
}
