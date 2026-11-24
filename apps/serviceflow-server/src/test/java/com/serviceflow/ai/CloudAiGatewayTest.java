package com.serviceflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.agent.Intent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

class CloudAiGatewayTest {
    private ChatModel chatModel;
    private CloudAiGateway gateway;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        gateway = new CloudAiGateway(
                chatModel,
                new AiCallExecutor(new SimpleMeterRegistry()),
                new ObjectMapper().findAndRegisterModules(),
                true,
                5000);
    }

    @Test
    void acceptsAValidChatIntent() {
        streamReturns("{\"intent\":\"CHAT\",\"reference\":\"UNCLEAR\",\"mention\":null}");

        assertThat(gateway.classifyProductReference("你好", ""))
                .contains(new AiGateway.ProductRouting(Intent.CHAT, AiGateway.ProductReference.UNCLEAR, null));
    }

    @Test
    void rejectsInvalidStructuredRoutingOutput() {
        streamReturns("{\"intent\":\"PRODUCT_ASK\",\"reference\":\"CURRENT\"}");
        assertThat(gateway.classifyProductReference("这款手机怎么样", "")).isEmpty();

        streamReturns("{\"intent\":\"PRODUCT_QUERY\",\"reference\":\"PREVIOUS\"}");
        assertThat(gateway.classifyProductReference("这款手机怎么样", "")).isEmpty();

        streamReturns("不是 JSON");
        assertThat(gateway.classifyProductReference("这款手机怎么样", "")).isEmpty();

        streamReturns("{\"intent\":\"PRODUCT_QUERY\",\"reference\":\"CURRENT\"} 多余文本");
        assertThat(gateway.classifyProductReference("这款手机怎么样", "")).isEmpty();
    }

    @Test
    void productRoutingKeepsOnlyMentionsCopiedFromTheUserMessage() {
        String question = "苹果16promax 的屏幕呢？";
        streamReturns("{\"intent\":\"PRODUCT_QUERY\",\"reference\":\"NEW\",\"mention\":\"苹果16promax\"}");
        assertThat(gateway.classifyProductReference(question, "当前商品：iPhone 16"))
                .contains(new AiGateway.ProductRouting(
                        Intent.PRODUCT_QUERY, AiGateway.ProductReference.NEW, "苹果16promax"));

        streamReturns("{\"intent\":\"PRODUCT_QUERY\",\"reference\":\"NEW\",\"mention\":\"iPhone 16 Pro Max\"}");
        assertThat(gateway.classifyProductReference(question, "当前商品：iPhone 16"))
                .contains(new AiGateway.ProductRouting(Intent.PRODUCT_QUERY, AiGateway.ProductReference.UNCLEAR, null));
    }

    @Test
    void streamsOnlyTextDeltas() {
        when(chatModel.stream(org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(Flux.just(response("你"), response("好")));
        List<String> tokens = new ArrayList<>();

        gateway.streamAnswer("system", "user", tokens::add);

        assertThat(tokens).containsExactly("你", "好");
    }

    @Test
    void rejectsResponseWithoutMessageContent() {
        when(chatModel.stream(org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(Flux.error(new IllegalStateException("no message content")));

        assertThatThrownBy(() -> gateway.classifyProductReference("hello", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no message content");
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

    @Test
    void requestsOrderSearchWithNativeTools() {
        toolsReturn(
                tool(
                        "search_orders",
                        """
                {"keyword":"耳机","fromDate":"2026-11-03","toDate":"2026-11-03"}
                """));
        var result = gateway.requestOrderTool("昨天买的耳机不要了", LocalDate.of(2026, 11, 4), false, List.of());
        assertThat(result)
                .contains(new AiGateway.OrderCommand.Search(new com.serviceflow.model.OrderModels.Search(
                        "耳机", null, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3))));
        var prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getContents()).contains("2026-11-04").contains("昨天买的耳机不要了");
        var options = (OpenAiChatOptions) prompt.getValue().getOptions();
        assertThat(options.getInternalToolExecutionEnabled()).isFalse();
        assertThat(options.getTools())
                .extracting(t -> t.getFunction().getName())
                .containsExactly(
                        "search_orders", "read_current_order", "select_order_candidate", "ask_order_clarification");
    }

    @Test
    void serializesOwnedCandidatesAndRejectsIgnoredFilters() {
        toolsReturn(tool("select_order_candidate", "{\"index\":1}"));
        var candidates = List.of(new com.serviceflow.model.OrderModels.Summary(
                "SF202611030001",
                "PAID",
                new java.math.BigDecimal("299.00"),
                java.time.Instant.parse("2026-11-03T01:00:00Z"),
                List.of()));
        assertThat(gateway.requestOrderTool("查一下便宜的那笔", LocalDate.of(2026, 11, 4), false, candidates))
                .contains(new AiGateway.OrderCommand.SelectCandidate(1));
        var prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getContents()).contains("299.00").doesNotContain("SF202611030001");
        toolsReturn(tool("read_current_order", "{\"keyword\":\"耳机\"}"));
        assertThatThrownBy(() -> gateway.requestOrderTool("耳机不要了", LocalDate.now(), true, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnrecognizedMultipleOrAbsentOrderTools() {
        toolsReturn(tool("unknown", "{}"));
        assertThatThrownBy(() -> gateway.requestOrderTool("查询", LocalDate.now(), false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        toolsReturn(tool("read_current_order", "{}"), tool("ask_order_clarification", "{}"));
        assertThatThrownBy(() -> gateway.requestOrderTool("查询", LocalDate.now(), false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        toolsReturn();
        assertThatThrownBy(() -> gateway.requestOrderTool("查询", LocalDate.now(), false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AssistantMessage.ToolCall tool(String name, String arguments) {
        return new AssistantMessage.ToolCall(name, "function", name, arguments);
    }

    private void toolsReturn(AssistantMessage.ToolCall... tools) {
        when(chatModel.call(org.mockito.ArgumentMatchers.any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(tools))
                        .build()))));
    }

    private void streamReturns(String content) {
        when(chatModel.stream(org.mockito.ArgumentMatchers.any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(Flux.just(response(content)));
    }
}
