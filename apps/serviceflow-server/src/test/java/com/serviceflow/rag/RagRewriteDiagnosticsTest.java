package com.serviceflow.rag;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.serviceflow.ai.AiGateway;
import com.serviceflow.config.ServiceFlowProperties;
import com.serviceflow.mapper.KnowledgeMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

/** Local HTTP stub + deterministic gateway responses; no cloud-model quality claim. */
class RagRewriteDiagnosticsTest {
    private static final String ORIGINAL = "6.12英寸都能显示内容，连圆角都算可视区，对吗？";
    private static final String DRIFTED = "这款手机的户外峰值亮度是多少？";
    private WireMockServer http;
    private AiGateway ai;
    private MilvusRagService rag;
    private RerankerClient reranker;
    private EmbeddingClient embedding;
    private SimpleMeterRegistry metrics;

    @TempDir
    Path traces;

    private JsonNode fixture;

    @BeforeEach
    void setup() throws Exception {
        try (var input = getClass().getResourceAsStream("/rag/screen-evidence.json")) {
            fixture = new ObjectMapper().readTree(input);
        }
        assertThat(fixture.path("query").asText()).isEqualTo(ORIGINAL);
        http = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        http.start();
        ai = mock(AiGateway.class);
        reranker = mock(RerankerClient.class);
        embedding = mock(EmbeddingClient.class);
        when(embedding.embed(anyString())).thenReturn(new float[] {1});
        var knowledge = mock(KnowledgeMapper.class);
        when(knowledge.activeVersionIds(anyString(), anyList())).thenReturn(List.of(25L));
        metrics = new SimpleMeterRegistry();
        var config = new ServiceFlowProperties(
                null,
                null,
                null,
                null,
                new ServiceFlowProperties.Rag(
                        "cloud", http.baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(1), "fixture", 1));
        rag = new MilvusRagService(
                config,
                embedding,
                reranker,
                knowledge,
                ai,
                metrics,
                new RagTraceRecorder(true, traces.toString(), new ObjectMapper()));
        ((AtomicBoolean) ReflectionTestUtils.getField(rag, "collectionReady")).set(true);
        when(reranker.rank(anyString(), anyList(), anyInt())).thenReturn(List.of("fixture-brightness"));
        var body = Map.of(
                "code",
                0,
                "data",
                List.of(Map.of(
                        "chunkId",
                        "fixture-brightness",
                        "title",
                        "iPhone 16 美国规格",
                        "source",
                        "iphone16-us.md",
                        "content",
                        fixture.path("brightness").asText(),
                        "distance",
                        0.9)));
        http.stubFor(post(urlEqualTo("/v2/vectordb/entities/hybrid_search"))
                .willReturn(okJson(new ObjectMapper().writeValueAsString(body))));
    }

    @AfterEach
    void close() {
        http.stop();
        metrics.close();
    }

    @Test
    void rewrittenRetrievalMustStillBeGradedAgainstOriginalUserQuestion() throws Exception {
        when(ai.rewriteQuery(ORIGINAL)).thenReturn(DRIFTED);
        when(ai.evaluateEvidence(anyString(), anyList()))
                .thenAnswer(call -> new AiGateway.EvidenceEvaluation(
                        DRIFTED.equals(call.getArgument(0)),
                        DRIFTED.equals(call.getArgument(0)) ? List.of("fixture-brightness") : List.of()));
        var result = rag.search(ORIGINAL, "PRODUCT_MANUAL", List.of(29L));
        assertThat(result.sufficient()).isFalse();
        assertThat(result.evidence()).isEmpty();
        verify(ai, times(2)).evaluateEvidence(eq(ORIGINAL), anyList());
        verify(ai, never()).evaluateEvidence(eq(DRIFTED), anyList());
        var trace = trace(result);
        assertThat(trace.path("stages").size()).isEqualTo(7);
        assertThat(trace.path("stages").get(4).path("query").asText()).isEqualTo(DRIFTED);
        assertThat(trace.path("stages").get(6).path("query").asText()).isEqualTo(ORIGINAL);
        assertThat(trace.path("stages")
                        .get(6)
                        .path("decisions")
                        .get(0)
                        .path("outputRank")
                        .asInt())
                .isZero();
    }

    @Test
    void aRecoveredSecondPassMustNotHideTheFirstGraderFailure() {
        when(ai.rewriteQuery(ORIGINAL)).thenReturn("屏幕圆角可视面积");
        when(ai.evaluateEvidence(anyString(), anyList()))
                .thenThrow(new IllegalStateException("controlled first-pass failure"))
                .thenReturn(new AiGateway.EvidenceEvaluation(true, List.of("fixture-brightness")));
        var result = rag.search(ORIGINAL, "PRODUCT_MANUAL", List.of(29L));
        assertThat(result.degraded()).isTrue();
    }

    @Test
    void screenFalseNegativeShapeIsVisibleAtTheGraderRatherThanMistakenForEmptyRecall() throws Exception {
        screenCandidate();
        when(ai.evaluateEvidence(anyString(), anyList()))
                .thenReturn(new AiGateway.EvidenceEvaluation(false, List.of()));
        when(ai.rewriteQuery(ORIGINAL)).thenReturn(ORIGINAL);
        var result = rag.search(ORIGINAL, "PRODUCT_MANUAL", List.of(29L));
        assertThat(result.sufficient()).isFalse();
        assertThat(result.evidence()).isEmpty();
        var trace = trace(result);
        assertThat(trace.path("stages")
                        .get(0)
                        .path("output")
                        .get(0)
                        .path("chunkId")
                        .asText())
                .isEqualTo("fixture-display");
        assertThat(trace.path("stages").get(2).path("input").size()).isEqualTo(1);
        assertThat(trace.path("stages").get(2).path("output").isEmpty()).isTrue();
        assertThat(trace.path("stages").get(2).path("reason").asText()).isEqualTo("grader_insufficient");
        // Diagnostic metadata includes no source body; original paragraphs remain in the separate evidence package.
        assertThat(trace.toString()).doesNotContain(fixture.path("display").asText());
    }

    @Test
    void counterevidenceCanAnswerAnIncorrectPremiseWithoutARewrite() throws Exception {
        screenCandidate();
        when(ai.evaluateEvidence(eq(ORIGINAL), anyList()))
                .thenReturn(new AiGateway.EvidenceEvaluation(true, List.of("fixture-display")));
        var result = rag.search(ORIGINAL, "PRODUCT_MANUAL", List.of(29L));
        assertThat(result.sufficient()).isTrue();
        assertThat(result.evidence().getFirst().content()).contains("实际可视面积更小");
        verify(ai, never()).rewriteQuery(anyString());
        assertThat(trace(result).path("stages").size()).isEqualTo(3);
    }

    @Test
    void emptyRecallSkipsRerankerAndGraderAndRecordsTheBoundary() throws Exception {
        http.stubFor(
                post(urlEqualTo("/v2/vectordb/entities/hybrid_search")).willReturn(okJson("{\"code\":0,\"data\":[]}")));
        when(ai.rewriteQuery(ORIGINAL)).thenReturn(ORIGINAL);
        var result = rag.search(ORIGINAL, "PRODUCT_MANUAL", List.of(29L));
        assertThat(result.sufficient()).isFalse();
        verifyNoInteractions(reranker);
        verify(ai, never()).evaluateEvidence(anyString(), anyList());
        assertThat(trace(result).path("stages").get(0).path("reason").asText()).isEqualTo("empty_recall");
    }

    @Test
    void rerankerFailureIsExplicitWhileFallbackEvidenceRemainsBounded() throws Exception {
        when(reranker.rank(anyString(), anyList(), anyInt()))
                .thenThrow(new IllegalStateException("controlled reranker failure"));
        when(ai.evaluateEvidence(anyString(), anyList()))
                .thenReturn(new AiGateway.EvidenceEvaluation(true, List.of("fixture-brightness")));
        var result = rag.search(ORIGINAL, "PRODUCT_MANUAL", List.of(29L));
        assertThat(result.degraded()).isTrue();
        assertThat(trace(result).path("stages").get(1).path("reason").asText()).isEqualTo("reranker_error_rrf_top5");
    }

    @Test
    void rewriteFailurePreservesPartialEvidenceAndMarksInsufficiency() throws Exception {
        when(ai.evaluateEvidence(anyString(), anyList()))
                .thenReturn(new AiGateway.EvidenceEvaluation(false, List.of("fixture-brightness")));
        when(ai.rewriteQuery(ORIGINAL)).thenThrow(new IllegalStateException("controlled rewrite failure"));
        var result = rag.search(ORIGINAL, "PRODUCT_MANUAL", List.of(29L));
        assertThat(result.degraded()).isTrue();
        assertThat(result.sufficient()).isFalse();
        assertThat(result.evidence()).hasSize(1);
        assertThat(trace(result).path("stages").get(3).path("reason").asText())
                .isEqualTo("rewrite_path_failed:IllegalStateException");
    }

    @Test
    void twoPassesKeepProductVersionAndOriginalQuestionThroughFinalFactSelection() throws Exception {
        String rewritten = "Galaxy S24 的圆角屏幕可视区域"; // Deliberate entity drift cannot change the database scope.
        when(ai.rewriteQuery(ORIGINAL)).thenReturn(rewritten);
        when(reranker.rank(anyString(), anyList(), anyInt()))
                .thenAnswer(call -> List.of(((List<RerankerClient.Document>) call.getArgument(1))
                        .getFirst()
                        .id()));
        when(ai.evaluateEvidence(anyString(), anyList())).thenAnswer(call -> {
            var candidates = (List<AiGateway.EvidenceCandidate>) call.getArgument(1);
            String id = candidates.getFirst().chunkId();
            return new AiGateway.EvidenceEvaluation(id.equals("fixture-display"), List.of(id));
        });
        http.stubFor(post(urlEqualTo("/v2/vectordb/entities/hybrid_search"))
                .withRequestBody(matchingJsonPath("$.search[1].data[0]", equalTo(rewritten)))
                .willReturn(okJson(new ObjectMapper()
                        .writeValueAsString(Map.of(
                                "code",
                                0,
                                "data",
                                List.of(Map.of(
                                        "chunkId",
                                        "fixture-display",
                                        "title",
                                        "iPhone 16 美国规格",
                                        "source",
                                        "iphone16-us.md",
                                        "content",
                                        fixture.path("display").asText(),
                                        "distance",
                                        0.9)))))));
        var result = rag.search(ORIGINAL, "PRODUCT_MANUAL", List.of(29L));
        assertThat(result.sufficient()).isTrue();
        assertThat(result.degraded()).isFalse();
        verify(ai, times(2)).evaluateEvidence(eq(ORIGINAL), anyList());
        verify(embedding).embed(ORIGINAL);
        verify(embedding).embed(rewritten);
        String filter = "documentType == \"PRODUCT_MANUAL\" and documentVersionId in [25] and productId in [29]";
        http.verify(
                2,
                postRequestedFor(urlEqualTo("/v2/vectordb/entities/hybrid_search"))
                        .withRequestBody(matchingJsonPath("$.search[0].filter", equalTo(filter)))
                        .withRequestBody(matchingJsonPath("$.search[1].filter", equalTo(filter))));
        var record = trace(result);
        assertThat(record.path("stages").size()).isEqualTo(7);
        assertThat(record.path("productIds").get(0).asLong()).isEqualTo(29);
        assertThat(record.path("activeVersionIds").get(0).asLong()).isEqualTo(25);
        var mapper = new ObjectMapper();
        var context = com.serviceflow.ai.GroundedAnswerComposer.context(
                Map.of("名称", "iPhone 16", "SKU", "APPLE-IP16-US-128"), result.evidence());
        int id = ((List<?>) context.get("facts")).size() - 1;
        String answer = com.serviceflow.ai.GroundedAnswerComposer.compose(
                mapper.writeValueAsString(context), "{\"factIds\":[" + id + "],\"insufficient\":false}", mapper);
        assertThat(answer).contains("实际可视面积更小").doesNotContain("户外峰值", "Galaxy");
    }

    @Test
    void failedSecondRecallKeepsFirstPartialEvidenceAndOriginalScope() throws Exception {
        when(ai.evaluateEvidence(anyString(), anyList()))
                .thenReturn(new AiGateway.EvidenceEvaluation(false, List.of("fixture-brightness")));
        when(ai.rewriteQuery(ORIGINAL)).thenReturn(DRIFTED);
        http.stubFor(post(urlEqualTo("/v2/vectordb/entities/hybrid_search"))
                .withRequestBody(matchingJsonPath("$.search[1].data[0]", equalTo(DRIFTED)))
                .willReturn(serverError()));
        var result = rag.search(ORIGINAL, "PRODUCT_MANUAL", List.of(29L));
        assertThat(result.sufficient()).isFalse();
        assertThat(result.degraded()).isTrue();
        assertThat(result.evidence()).extracting(RagService.Evidence::chunkId).containsExactly("fixture-brightness");
        verify(ai).evaluateEvidence(eq(ORIGINAL), anyList());
        assertThat(trace(result).path("stages").get(4).path("reason").asText()).startsWith("retrieval_failed:");
    }

    @Test
    void traceWritingRequiresBothEvaluationModeAndAnExplicitDirectory() throws Exception {
        var result = new RagService.SearchResult(List.of(), false, false);
        var disabled = new RagTraceRecorder(false, traces.resolve("disabled").toString(), new ObjectMapper());
        assertThat(disabled.begin(ORIGINAL, "PRODUCT_MANUAL", List.of()).finish(result, System.nanoTime()))
                .isNull();
        assertThat(Files.exists(traces.resolve("disabled"))).isFalse();
        var noPath = new RagTraceRecorder(true, "", new ObjectMapper());
        assertThat(noPath.begin(ORIGINAL, "PRODUCT_MANUAL", List.of()).finish(result, System.nanoTime()))
                .isNull();
        Path blocked = traces.resolve("not-a-directory");
        Files.writeString(blocked, "fixture");
        var failed = new RagTraceRecorder(true, blocked.toString(), new ObjectMapper());
        assertThat(failed.begin(ORIGINAL, "PRODUCT_MANUAL", List.of()).finish(result, System.nanoTime()))
                .isNull();
    }

    private void screenCandidate() throws Exception {
        http.stubFor(post(urlEqualTo("/v2/vectordb/entities/hybrid_search"))
                .willReturn(okJson(new ObjectMapper()
                        .writeValueAsString(Map.of(
                                "code",
                                0,
                                "data",
                                List.of(Map.of(
                                        "chunkId",
                                        "fixture-display",
                                        "title",
                                        "iPhone 16 美国规格",
                                        "source",
                                        "iphone16-us.md",
                                        "content",
                                        fixture.path("display").asText(),
                                        "distance",
                                        0.9)))))));
        when(reranker.rank(anyString(), anyList(), anyInt())).thenReturn(List.of("fixture-display"));
    }

    private JsonNode trace(RagService.SearchResult result) throws Exception {
        assertThat(result.diagnosticId()).isNotBlank();
        var record = new ObjectMapper().readTree(Files.readString(traces.resolve(result.diagnosticId() + ".raw.json")));
        assertThat(record.path("originalQuery").asText()).isEqualTo(ORIGINAL);
        for (var stage : record.path("stages"))
            assertThat(stage.path("elapsedMs").asDouble()).isGreaterThanOrEqualTo(0);
        return record;
    }
}
