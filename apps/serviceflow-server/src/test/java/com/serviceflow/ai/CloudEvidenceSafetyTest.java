package com.serviceflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.config.ServiceFlowProperties;
import com.serviceflow.mapper.KnowledgeMapper;
import com.serviceflow.rag.EmbeddingClient;
import com.serviceflow.rag.MilvusRagService;
import com.serviceflow.rag.RagService;
import com.serviceflow.rag.RerankerClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

/** No database, embedding service, or remote model is called by these safety checks. */
class CloudEvidenceSafetyTest {
    private StreamingChatModel model;
    private AiCallExecutor calls;
    private CloudAiGateway gateway;
    private SimpleMeterRegistry metrics;
    private final List<AiGateway.EvidenceCandidate> candidates =
            List.of(new AiGateway.EvidenceCandidate("a", "资料", "屏幕为六英寸。"));

    @BeforeEach
    void setUp() {
        model = mock(StreamingChatModel.class);
        metrics = new SimpleMeterRegistry();
        calls = new AiCallExecutor(metrics);
        gateway = new CloudAiGateway(model, calls, new ObjectMapper());
    }

    @AfterEach
    void close() {
        calls.close();
        metrics.close();
    }

    @Test
    void preservesPartialEvidenceWhileInsufficient() {
        returns("{\"sufficient\":false,\"rankedChunkIds\":[\"a\"]}");
        var result = gateway.evaluateEvidence("尺寸和材质分别是什么？", candidates);
        assertThat(result.sufficient()).isFalse();
        assertThat(result.rankedChunkIds()).containsExactly("a");
    }

    @Test
    void rejectsInventedIdsEvenWhenModelClaimsSufficiency() {
        returns("{\"sufficient\":true,\"rankedChunkIds\":[\"invented\"]}");
        var result = gateway.evaluateEvidence("屏幕尺寸？", candidates);
        assertThat(result.sufficient()).isFalse();
        assertThat(result.rankedChunkIds()).isEmpty();
    }

    @Test
    void emptySelectionCannotBeSufficient() {
        returns("{\"sufficient\":true,\"rankedChunkIds\":[]}");
        assertThat(gateway.evaluateEvidence("屏幕尺寸？", candidates).sufficient()).isFalse();
    }

    @Test
    void malformedJsonFailsClosedAtGatewayBoundary() {
        returns("{\"sufficient\":");
        assertThatThrownBy(() -> gateway.evaluateEvidence("屏幕尺寸？", candidates))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cloud evidence grader failed");
    }

    @Test
    void timeoutUsesTheActualGatewayBudget() {
        when(model.stream(any(Prompt.class))).thenReturn(Flux.never());
        long started = System.nanoTime();
        assertThatThrownBy(() -> gateway.evaluateEvidence("屏幕尺寸？", candidates))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cloud evidence grader failed");
        assertThat(Duration.ofNanos(System.nanoTime() - started).toSeconds()).isBetween(19L, 30L);
    }

    @Test
    void graderFailureKeepsEvidenceButMarksRagDegradedAndInsufficient() {
        var properties = new ServiceFlowProperties(
                null,
                null,
                null,
                null,
                new ServiceFlowProperties.Rag(
                        "cloud", "http://127.0.0.1:1", Duration.ofSeconds(1), Duration.ofSeconds(1), "unused", 1));
        var failing = mock(AiGateway.class);
        when(failing.evaluateEvidence(any(), any())).thenThrow(new IllegalStateException("controlled grader failure"));
        var rag = new MilvusRagService(
                properties,
                mock(EmbeddingClient.class),
                mock(RerankerClient.class),
                mock(KnowledgeMapper.class),
                failing,
                metrics);
        var evidence = List.of(new RagService.Evidence("a", "资料", "fixture", "屏幕为六英寸。", 1.0));
        Object result = ReflectionTestUtils.invokeMethod(rag, "evaluate", "屏幕尺寸？", evidence);
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(result, "sufficient"))
                .isFalse();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(result, "degraded"))
                .isTrue();
        assertThat((List<?>) ReflectionTestUtils.invokeMethod(result, "evidence"))
                .isEqualTo(evidence);
        assertThat(metrics.counter("serviceflow.rag.grader.degraded").count()).isEqualTo(1.0);
    }

    private void returns(String text) {
        when(model.stream(any(Prompt.class)))
                .thenReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage(text))))));
    }
}
