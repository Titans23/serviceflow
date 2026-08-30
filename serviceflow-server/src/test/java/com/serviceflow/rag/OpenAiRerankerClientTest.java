package com.serviceflow.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.config.ServiceFlowProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiRerankerClientTest {

    private MockRestServiceServer server;
    private OpenAiRerankerClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiRerankerClient(builder, properties(), new ObjectMapper(), false);
    }

    @Test
    void sendsDashScopeNativePayloadAndMapsRankedIndexesToIds() {
        server.expect(once(), requestTo("https://reranker.test/api/v1/services/rerank"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("qwen3-rerank"))
                .andExpect(jsonPath("$.input.query").value("续航"))
                .andExpect(jsonPath("$.input.documents[0]").value("first text"))
                .andExpect(jsonPath("$.parameters.top_n").value(2))
                .andExpect(jsonPath("$.parameters.return_documents").value(false))
                .andRespond(withSuccess(
                        """
                        {"output":{"results":[
                          {"index":1,"relevance_score":0.91},
                          {"index":0,"relevance_score":0.42}
                        ]}}
                        """,
                        MediaType.APPLICATION_JSON));

        List<String> result = client.rank(
                "续航",
                List.of(
                        new RerankerClient.Document("first", "first text"),
                        new RerankerClient.Document("second", "second text")),
                2);

        assertThat(result).containsExactly("second", "first");
        server.verify();
    }

    private ServiceFlowProperties properties() {
        return new ServiceFlowProperties(
                null,
                new ServiceFlowProperties.Ai(
                        "cloud",
                        new ServiceFlowProperties.Ai.Chat("https://chat.test", "test-key", "qwen-plus"),
                        new ServiceFlowProperties.Ai.Embedding("https://embedding.test", "test-key", "embedding"),
                        new ServiceFlowProperties.Ai.Reranker(
                                "https://reranker.test/api/v1", "test-key", "qwen3-rerank", "/services/rerank")),
                null,
                null,
                null);
    }
}
