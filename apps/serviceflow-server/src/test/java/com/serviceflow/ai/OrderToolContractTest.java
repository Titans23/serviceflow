package com.serviceflow.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.serviceflow.exception.ServiceFlowException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

class OrderToolContractTest {
    private final WireMockServer server = new WireMockServer(0);
    private final AiCallExecutor calls = new AiCallExecutor(new SimpleMeterRegistry());

    @AfterEach
    void stop() {
        server.stop();
        calls.close();
    }

    @Test
    void sendsNativeToolDefinitionsAndReturnsTheRequestWithoutExecutingOrReplanning() {
        server.start();
        server.stubFor(
                post(urlEqualTo("/v1/chat/completions"))
                        .willReturn(
                                okJson(
                                        """
                {"id":"mock","model":"qwen-plus","choices":[{"index":0,"finish_reason":"tool_calls",
                  "message":{"role":"assistant","content":null,"tool_calls":[
                    {"id":"call-1","type":"function","function":{"name":"search_orders",
                       "arguments":"{\\"keyword\\":\\"耳机\\"}"}}]}}]}
                """)));
        assertThat(gateway(Duration.ofSeconds(5)).requestOrderTool("查耳机订单", LocalDate.now(), false, List.of()))
                .contains(new AiGateway.OrderCommand.Search(
                        new com.serviceflow.model.OrderModels.Search("耳机", null, null, null)));
        server.verify(
                1,
                postRequestedFor(urlEqualTo("/v1/chat/completions"))
                        .withRequestBody(matchingJsonPath("$.tools[?(@.function.name == 'search_orders')]"))
                        .withRequestBody(matchingJsonPath("$.stream", equalTo("false"))));
    }

    @Test
    void slowProviderStopsPlanningAtTheConfiguredDeadline() {
        server.start();
        server.stubFor(
                post(urlEqualTo("/v1/chat/completions")).willReturn(okJson("{}").withFixedDelay(2_000)));
        assertThatThrownBy(() ->
                        gateway(Duration.ofMillis(250)).requestOrderTool("查耳机订单", LocalDate.now(), false, List.of()))
                .isInstanceOf(ServiceFlowException.class)
                .hasMessageContaining("超时");
    }

    private CloudAiGateway gateway(Duration timeout) {
        var http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        var api = OpenAiApi.builder()
                .baseUrl(server.baseUrl())
                .apiKey("test")
                .restClientBuilder(RestClient.builder().requestFactory(new JdkClientHttpRequestFactory(http)))
                .build();
        var model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model("qwen-plus").build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .build();
        return new CloudAiGateway(model, calls, new ObjectMapper(), true, timeout.toMillis());
    }
}
