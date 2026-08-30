package com.serviceflow.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.agent.Intent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import reactor.core.publisher.Flux;

class CloudAiGatewayTest {
    private StreamingChatModel streamingChatModel;
    private CloudAiGateway gateway;

    @BeforeEach
    void setUp() {
        streamingChatModel = mock(StreamingChatModel.class);
        gateway = new CloudAiGateway(
                streamingChatModel, new AiCallExecutor(new SimpleMeterRegistry()), new ObjectMapper());
    }

    @Test
    void classifiesUsingSpringAiResponse() {
        streamReturns("PRODUCT_QUERY");

        assertThat(gateway.classify("这款手机怎么样")).isEqualTo(Intent.PRODUCT_QUERY);
    }

    @Test
    void streamsOnlyTextDeltas() {
        when(streamingChatModel.stream(
                        org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(Flux.just(response("你"), response("好")));
        List<String> tokens = new ArrayList<>();

        gateway.streamAnswer("system", "user", tokens::add);

        assertThat(tokens).containsExactly("你", "好");
    }

    @Test
    void rejectsResponseWithoutMessageContent() {
        when(streamingChatModel.stream(
                        org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(Flux.error(new IllegalStateException("no message content")));

        assertThatThrownBy(() -> gateway.classify("hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no message content");
    }

    @Test
    void classifiesAfterACloudCall() {
        streamReturns("CHAT");

        assertThat(gateway.classify("你好")).isEqualTo(Intent.CHAT);
    }

    @Test
    void evidenceEvaluationKeepsOnlyKnownDistinctChunkIds() throws Exception {
        String payload = new ObjectMapper()
                .writeValueAsString(Map.of("sufficient", true, "rankedChunkIds", List.of("c2", "unknown", "c2", "c1")));
        streamReturns(payload);

        AiGateway.EvidenceEvaluation result = gateway.evaluateEvidence(
                "query",
                List.of(
                        new AiGateway.EvidenceCandidate("c1", "one", "content one"),
                        new AiGateway.EvidenceCandidate("c2", "two", "content two")));

        assertThat(result.sufficient()).isTrue();
        assertThat(result.rankedChunkIds()).containsExactly("c2", "c1");
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    private void streamReturns(String content) {
        when(streamingChatModel.stream(
                        org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(Flux.just(response(content)));
    }
}
