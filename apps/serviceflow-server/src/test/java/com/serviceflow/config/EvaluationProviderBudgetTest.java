package com.serviceflow.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.ai.AiCallExecutor;
import com.serviceflow.ai.CloudAiGateway;
import com.serviceflow.rag.OpenAiCompatibleEmbeddingModel;
import com.serviceflow.rag.OpenAiEmbeddingClient;
import com.serviceflow.rag.OpenAiRerankerClient;
import com.serviceflow.rag.RerankerClient;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

class EvaluationProviderBudgetTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @ValueSource(ints = {27, 63})
    void realClientsShareCapAndDoNotRetryInFreshJvm(int limit) throws Exception {
        // Transport retry policy is a JVM-startup setting, not a mutable test property.
        Path output = temporary.resolve("budget-stub.log");
        Path arguments = temporary.resolve("java.args");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Files.writeString(
                arguments,
                "-Xmx256m\n-Djdk.httpclient.redirects.retrylimit=1\n-cp\n\""
                        + classpath.replace("\\", "\\\\").replace("\"", "\\\"") + "\"\n"
                        + EvaluationProviderBudgetTest.class.getName() + "\n" + limit);
        List<String> command =
                List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "@" + arguments);
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        try {
            assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue())
                    .withFailMessage(Files.readString(output))
                    .isZero();
            String expected =
                    "BUDGET_STUB_PASS received=" + limit + " unique=" + limit + " blocked=3 redirects=0 startup=0";
            assertThat(Files.readString(output)).contains(expected);
            System.out.println(expected);
            if (limit == 63) {
                assertThat(Files.readString(output)).contains("FAILURE_STOP_PASS");
                assertThat(Files.readString(output)).contains("FAILURE_STOP_NORMAL_PASS");
                System.out.println("FAILURE_STOP_NORMAL_PASS split DONE and subsequent embedding/rerank allowed");
                System.out.println(
                        "FAILURE_STOP_PASS http429 network brokenBody truncatedSse timeout; subsequent wire requests=0");
            }
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    void ordinaryModeHasNoBudgetAndKeepsExistingRetrySettings() {
        context("http://127.0.0.1:1", false).run(application -> {
            assertThat(application).hasNotFailed().doesNotHaveBean(EvaluationProviderBudget.class);
            for (String name : List.of("chat", "embedding", "reranker")) {
                assertThat(application
                                .getBean(RetryRegistry.class)
                                .retry(name)
                                .getRetryConfig()
                                .getMaxAttempts())
                        .isEqualTo(2);
            }
            // Context construction must not change the process-wide transport retry setting.
            assertThat(System.getProperty("jdk.httpclient.redirects.retrylimit"))
                    .isNull();
        });
    }

    @Test
    void cannotEnableBudgetWithoutExplicitJvmStartupFlag() {
        context("http://127.0.0.1:1", true).run(application -> {
            assertThat(application).hasFailed();
            assertThat(application.getStartupFailure())
                    .hasStackTraceContaining("start Java 21 with -Djdk.httpclient.redirects.retrylimit=1");
        });
    }

    public static void main(String[] args) throws Exception {
        int limit = args.length == 0 ? 27 : Integer.parseInt(args[0]);
        AtomicInteger received = new AtomicInteger();
        AtomicInteger redirects = new AtomicInteger();
        var counts = new ConcurrentHashMap<String, AtomicInteger>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestURI().getPath().equals("/redirect-target")) {
                redirects.incrementAndGet();
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
                return;
            }
            received.incrementAndGet();
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var marker = Pattern.compile(
                            "fixture-[0-9]+-(success|error|drop|redirect|ratelimit|truncated|brokenbody|splitdone)")
                    .matcher(request);
            if (!marker.find()) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
                return;
            }
            String id = marker.group();
            counts.computeIfAbsent(id, ignored -> new AtomicInteger()).incrementAndGet();
            String mode = marker.group(1);
            if (mode.equals("drop")) {
                exchange.close();
                return;
            }
            if (mode.equals("brokenbody")) {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, 1000);
                exchange.getResponseBody().write("{}".getBytes(StandardCharsets.UTF_8));
                exchange.close();
                return;
            }
            if (mode.equals("redirect")) {
                exchange.getResponseHeaders().set("Location", "/redirect-target");
                exchange.sendResponseHeaders(307, -1);
                exchange.close();
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String response;
            String type = "application/json";
            if (mode.equals("error") || mode.equals("ratelimit")) {
                response = "{\"error\":{\"message\":\"synthetic failure\",\"type\":\"server_error\"}}";
            } else if (path.endsWith("/embeddings")) {
                response = "{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2]}]}";
            } else if (path.endsWith("/rerank")) {
                response = "{\"results\":[{\"index\":0,\"relevance_score\":0.9}]}";
            } else {
                type = "text/event-stream";
                response =
                        "data: {\"id\":\"stub\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"stub\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"PRODUCT_QUERY\"},\"finish_reason\":null}]}\n\n"
                                + "data: {\"id\":\"stub\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"stub\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                                + "data: [DONE]\n\n";
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            if (mode.equals("splitdone")) {
                exchange.getResponseHeaders().set("Content-Type", type);
                exchange.sendResponseHeaders(200, 0);
                int split = response.indexOf("[DONE]") + 3;
                exchange.getResponseBody().write(response.substring(0, split).getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                try {
                    Thread.sleep(25);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
                exchange.getResponseBody().write(response.substring(split).getBytes(StandardCharsets.UTF_8));
                exchange.close();
                return;
            }
            if (mode.equals("truncated"))
                bytes = response.replace("data: [DONE]\n\n", "").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", type);
            exchange.sendResponseHeaders(
                    mode.equals("error") ? 500 : mode.equals("ratelimit") ? 429 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            for (String invalid : List.of(
                    "serviceflow.evaluation.enabled=false",
                    "serviceflow.evaluation.provider-request-limit=0",
                    "spring.ai.retry.max-attempts=2",
                    "resilience4j.retry.instances.chat.max-attempts=2",
                    "resilience4j.retry.instances.embedding.max-attempts=2",
                    "resilience4j.retry.instances.reranker.max-attempts=2",
                    "spring.rabbitmq.listener.simple.auto-startup=true",
                    "spring.rabbitmq.listener.direct.auto-startup=true",
                    "serviceflow.ai.http1-enabled=true")) {
                context("http://127.0.0.1:" + server.getAddress().getPort(), true)
                        .withPropertyValues(invalid)
                        .run(application -> assertThat(application).hasFailed());
            }
            context("http://127.0.0.1:" + server.getAddress().getPort(), true)
                    .withPropertyValues("serviceflow.evaluation.provider-request-limit=" + limit)
                    .run(application -> {
                        assertThat(application).hasNotFailed();
                        assertThat(received.get()).isZero();
                        var chat = application.getBean(CloudAiGateway.class);
                        var embedding = application.getBean(OpenAiEmbeddingClient.class);
                        var reranker = application.getBean(OpenAiRerankerClient.class);
                        assertThat(AopUtils.isAopProxy(chat)).isTrue();
                        assertThat(AopUtils.isAopProxy(embedding)).isTrue();
                        assertThat(AopUtils.isAopProxy(reranker)).isTrue();
                        for (String name : List.of("chat", "embedding", "reranker")) {
                            assertThat(application
                                            .getBean(RetryRegistry.class)
                                            .retry(name)
                                            .getRetryConfig()
                                            .getMaxAttempts())
                                    .isEqualTo(1);
                        }
                        var documents = List.of(new RerankerClient.Document("chunk", "Public synthetic text"));
                        for (int i = 0; i < limit; i++) {
                            int number = i;
                            int group = i / 3;
                            String mode = group < 3
                                    ? "success"
                                    : List.of("error", "drop", "redirect").get((group - 3) % 3);
                            String input = "fixture-" + i + "-" + mode;
                            Runnable call =
                                    switch (i % 3) {
                                        case 0 -> group >= 6
                                                ? () -> application
                                                        .getBean(OpenAiChatModel.class)
                                                        .call(input)
                                                : () -> {
                                                    if (number == 3) chat.streamAnswer("test", input, token -> {});
                                                    else if (number == 6) chat.rewriteQuery(input);
                                                    else chat.classify(input);
                                                };
                                        case 1 -> () -> embedding.embed(input);
                                        default -> () -> reranker.rank(input, documents, 1);
                                    };
                            // An empty 307 streaming response may complete without a model answer.
                            try {
                                call.run();
                                assertThat(mode).isIn("success", "redirect");
                            } catch (RuntimeException failure) {
                                assertThat(mode).isNotEqualTo("success");
                            }
                            assertThat(received.get())
                                    .as("one wire attempt for fixture %s", i)
                                    .isEqualTo(i + 1);
                        }
                        assertThatThrownBy(() -> chat.classify("fixture-" + (limit + 1) + "-success"))
                                .hasStackTraceContaining("budget exhausted");
                        assertThatThrownBy(() -> embedding.embed("fixture-" + (limit + 2) + "-success"))
                                .hasStackTraceContaining("budget exhausted");
                        assertThatThrownBy(() -> reranker.rank("fixture-" + (limit + 3) + "-success", documents, 1))
                                .hasStackTraceContaining("budget exhausted");
                        assertThat(application
                                        .getBean(EvaluationProviderBudget.class)
                                        .used())
                                .isEqualTo(limit);
                        assertThat(received.get()).isEqualTo(limit);
                        assertThat(counts).hasSize(limit);
                        counts.values().forEach(count -> assertThat(count.get()).isEqualTo(1));
                        assertThat(redirects.get()).isZero();
                        System.out.println("BUDGET_STUB_PASS received=" + limit + " unique=" + limit
                                + " blocked=3 redirects=0 startup=0");
                    });
            if (limit == 63) {
                received.set(0);
                context("http://127.0.0.1:" + server.getAddress().getPort(), true)
                        .withPropertyValues(
                                "serviceflow.evaluation.stop-on-provider-failure=true",
                                "serviceflow.evaluation.provider-request-limit=63")
                        .run(application -> {
                            assertThat(application.getBean(CloudAiGateway.class).classify("fixture-1-splitdone"))
                                    .isEqualTo(com.serviceflow.agent.Intent.PRODUCT_QUERY);
                            application.getBean(OpenAiEmbeddingClient.class).embed("fixture-2-success");
                            application
                                    .getBean(OpenAiRerankerClient.class)
                                    .rank(
                                            "fixture-3-success",
                                            List.of(new RerankerClient.Document("chunk", "synthetic")),
                                            1);
                            assertThat(application
                                            .getBean(EvaluationProviderBudget.class)
                                            .stopped())
                                    .isFalse();
                            assertThat(received.get()).isEqualTo(3);
                        });
                System.out.println("FAILURE_STOP_NORMAL_PASS split DONE and subsequent embedding/rerank allowed");
                for (String mode : List.of("ratelimit", "drop", "brokenbody", "truncated", "timeout")) {
                    received.set(0);
                    context("http://127.0.0.1:" + server.getAddress().getPort(), true)
                            .withPropertyValues(
                                    "serviceflow.evaluation.stop-on-provider-failure=true",
                                    "serviceflow.evaluation.provider-request-limit=63")
                            .run(application -> {
                                assertThat(application).hasNotFailed();
                                var budget = application.getBean(EvaluationProviderBudget.class);
                                assertThatThrownBy(() -> {
                                            if (mode.equals("timeout")) {
                                                application
                                                        .getBean(AiCallExecutor.class)
                                                        .execute("chat", java.time.Duration.ofMillis(20), () -> {
                                                            try {
                                                                Thread.sleep(100);
                                                            } catch (InterruptedException error) {
                                                                Thread.currentThread()
                                                                        .interrupt();
                                                            }
                                                            return "late";
                                                        });
                                            } else if (mode.equals("drop")) {
                                                application
                                                        .getBean(OpenAiEmbeddingClient.class)
                                                        .embed("fixture-1-" + mode);
                                            } else if (mode.equals("brokenbody")) {
                                                application
                                                        .getBean(OpenAiRerankerClient.class)
                                                        .rank(
                                                                "fixture-1-" + mode,
                                                                List.of(new RerankerClient.Document(
                                                                        "chunk", "synthetic")),
                                                                1);
                                            } else {
                                                application
                                                        .getBean(CloudAiGateway.class)
                                                        .classify("fixture-1-" + mode);
                                            }
                                        })
                                        .isInstanceOf(RuntimeException.class);
                                assertThat(budget.stopped()).isTrue();
                                int count = received.get();
                                assertThat(count).isEqualTo(mode.equals("timeout") ? 0 : 1);
                                assertThatThrownBy(() -> application
                                                .getBean(OpenAiEmbeddingClient.class)
                                                .embed("fixture-2-success"))
                                        .hasStackTraceContaining("stopped after external failure");
                                assertThatThrownBy(() -> application
                                                .getBean(CloudAiGateway.class)
                                                .classify("fixture-3-success"))
                                        .hasStackTraceContaining("stopped after external failure");
                                assertThatThrownBy(() -> application
                                                .getBean(OpenAiRerankerClient.class)
                                                .rank(
                                                        "fixture-4-success",
                                                        List.of(new RerankerClient.Document("chunk", "synthetic")),
                                                        1))
                                        .hasStackTraceContaining("stopped after external failure");
                                assertThat(received.get()).isEqualTo(count);
                            });
                }
                System.out.println(
                        "FAILURE_STOP_PASS http429 network brokenBody truncatedSse timeout; subsequent wire requests=0");
            }
        } finally {
            server.stop(0);
        }
    }

    private static ApplicationContextRunner context(String base, boolean enabled) {
        var runner = new ApplicationContextRunner()
                .withInitializer(application -> {
                    try {
                        for (var source : new YamlPropertySourceLoader()
                                .load("application", new ClassPathResource("application.yml"))) {
                            application.getEnvironment().getPropertySources().addLast(source);
                        }
                    } catch (java.io.IOException failure) {
                        throw new IllegalStateException(failure);
                    }
                })
                .withConfiguration(AutoConfigurations.of(
                        AopAutoConfiguration.class,
                        RetryAutoConfiguration.class,
                        WebClientAutoConfiguration.class,
                        SpringAiRetryAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class,
                        OpenAiChatAutoConfiguration.class))
                .withUserConfiguration(
                        Fixtures.class,
                        RestClientConfiguration.class,
                        EvaluationProviderBudgetConfiguration.class,
                        OpenAiCompatibleEmbeddingModel.class,
                        OpenAiEmbeddingClient.class,
                        OpenAiRerankerClient.class,
                        CloudAiGateway.class,
                        AiCallExecutor.class)
                .withPropertyValues(
                        "serviceflow.ai.mode=cloud",
                        "serviceflow.rag.mode=cloud",
                        "serviceflow.rag.milvus-rest-url=http://127.0.0.1:2",
                        "serviceflow.evaluation.enabled=true",
                        "spring.ai.openai.base-url=" + base,
                        "spring.ai.openai.api-key=synthetic-stub-key",
                        "spring.ai.openai.embedding.base-url=" + base,
                        "serviceflow.ai.embedding.base-url=" + base,
                        "serviceflow.ai.embedding.api-key=synthetic-stub-key",
                        "serviceflow.ai.reranker.base-url=" + base,
                        "serviceflow.ai.reranker.api-key=synthetic-stub-key",
                        "serviceflow.ai.reranker.path=/rerank");
        if (enabled) {
            runner = runner.withPropertyValues(
                    "serviceflow.evaluation.provider-budget-enabled=true",
                    "serviceflow.evaluation.provider-request-limit=27",
                    "spring.ai.retry.max-attempts=1",
                    "resilience4j.retry.instances.chat.max-attempts=1",
                    "resilience4j.retry.instances.embedding.max-attempts=1",
                    "resilience4j.retry.instances.reranker.max-attempts=1",
                    "spring.rabbitmq.listener.simple.auto-startup=false",
                    "spring.rabbitmq.listener.direct.auto-startup=false");
        }
        return runner;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ServiceFlowProperties.class)
    static class Fixtures {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        ObservationRegistry observationRegistry() {
            return ObservationRegistry.NOOP;
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
