package com.serviceflow.config;

import jakarta.annotation.PreDestroy;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** One process, one shared cap. Only explicitly enabled for isolated evaluation. */
public final class EvaluationProviderBudget {
    private static final Logger LOG = LoggerFactory.getLogger(EvaluationProviderBudget.class);
    private static final String RETRY_FLAG = "-Djdk.httpclient.redirects.retrylimit=1";
    private final Set<String> providerOrigins = new HashSet<>();
    private final int limit;
    private final HttpClient client;
    private int used;
    private final boolean stopOnFailure;
    private String stoppedReason;

    EvaluationProviderBudget(Environment environment, ServiceFlowProperties properties) {
        require(
                environment.getProperty("serviceflow.evaluation.enabled", Boolean.class, false),
                "isolated evaluation required");
        require(
                Runtime.version().feature() == 21
                        && "1".equals(System.getProperty("jdk.httpclient.redirects.retrylimit"))
                        && ManagementFactory.getRuntimeMXBean()
                                .getInputArguments()
                                .contains(RETRY_FLAG),
                "start Java 21 with " + RETRY_FLAG);
        for (String property : new String[] {
            "spring.ai.retry.max-attempts",
            "resilience4j.retry.instances.chat.max-attempts",
            "resilience4j.retry.instances.embedding.max-attempts",
            "resilience4j.retry.instances.reranker.max-attempts"
        }) {
            require(environment.getProperty(property, Integer.class, 0) == 1, property + " must be 1");
        }
        for (String type : new String[] {"simple", "direct"}) {
            require(
                    "false".equals(environment.getProperty("spring.rabbitmq.listener." + type + ".auto-startup")),
                    "background ingestion listeners must be disabled");
        }
        require(
                !environment.getProperty("serviceflow.ai.http1-enabled", Boolean.class, false),
                "use only the evaluation HTTP client customization");
        limit = environment.getRequiredProperty("serviceflow.evaluation.provider-request-limit", Integer.class);
        require(limit > 0, "positive provider-request-limit required");
        stopOnFailure =
                environment.getProperty("serviceflow.evaluation.stop-on-provider-failure", Boolean.class, false);
        providerOrigins.add(origin(URI.create(properties.ai().embedding().baseUrl())));
        providerOrigins.add(origin(URI.create(properties.ai().reranker().baseUrl())));
        String base = environment.getRequiredProperty("spring.ai.openai.base-url");
        providerOrigins.add(origin(URI.create(environment.getProperty("spring.ai.openai.chat.base-url", base))));
        providerOrigins.add(origin(URI.create(environment.getProperty("spring.ai.openai.embedding.base-url", base))));
        require(
                !providerOrigins.contains(origin(URI.create(properties.rag().milvusRestUrl()))),
                "Milvus and model providers must have separate origins");
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        LOG.info("Evaluation provider budget enabled: limit={}, no automatic retries; restart resets budget", limit);
    }

    public void customize(RestClient.Builder builder) {
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(20));
        builder.requestFactory(factory).requestInterceptor((request, body, execution) -> {
            admit(request.getURI());
            try {
                ClientHttpResponse response = execution.execute(request, body);
                if (!stopOnFailure || !providerOrigins.contains(origin(request.getURI()))) return response;
                if (response.getStatusCode().isError())
                    stopAfterFailure("http_" + response.getStatusCode().value());
                return new ClientHttpResponse() {
                    @Override
                    public HttpStatusCode getStatusCode() throws IOException {
                        return response.getStatusCode();
                    }

                    @Override
                    public String getStatusText() throws IOException {
                        return response.getStatusText();
                    }

                    @Override
                    public HttpHeaders getHeaders() {
                        return response.getHeaders();
                    }

                    @Override
                    public void close() {
                        response.close();
                    }

                    @Override
                    public InputStream getBody() throws IOException {
                        return new FilterInputStream(response.getBody()) {
                            @Override
                            public int read() throws IOException {
                                try {
                                    return super.read();
                                } catch (IOException error) {
                                    stopAfterFailure("body_io");
                                    throw error;
                                }
                            }

                            @Override
                            public int read(byte[] bytes, int offset, int length) throws IOException {
                                try {
                                    return super.read(bytes, offset, length);
                                } catch (IOException error) {
                                    stopAfterFailure("body_io");
                                    throw error;
                                }
                            }
                        };
                    }
                };
            } catch (IOException | RuntimeException error) {
                if (providerOrigins.contains(origin(request.getURI()))) stopAfterFailure("transport");
                throw error;
            }
        });
    }

    void customize(WebClient.Builder builder) {
        var connector = new JdkClientHttpConnector(client);
        connector.setReadTimeout(Duration.ofSeconds(20));
        builder.clientConnector(connector)
                .filter((request, next) -> Mono.defer(() -> {
                    admit(request.url());
                    if (!providerOrigins.contains(origin(request.url()))) return next.exchange(request);
                    return next.exchange(request)
                            .map(this::observeResponse)
                            .doOnError(error -> stopAfterFailure("transport"));
                }));
    }

    private synchronized void admit(URI uri) {
        if (!providerOrigins.contains(origin(uri))) {
            return; // Shared RestClient also serves the local Milvus health check.
        }
        if (stoppedReason != null)
            throw new IllegalStateException("Evaluation provider calls stopped after external failure");
        if (used == limit) {
            LOG.warn(
                    "Evaluation provider budget exhausted: used={}, limit={}; request blocked before send",
                    used,
                    limit);
            throw new IllegalStateException("Evaluation provider request budget exhausted");
        }
        used++;
        // No URLs, payloads, credentials or model responses in the budget log.
        LOG.info("Evaluation provider request admitted: used={}, limit={}", used, limit);
    }

    synchronized int used() {
        return used;
    }

    public synchronized void stopAfterFailure(String reason) {
        if (stopOnFailure && stoppedReason == null) {
            stoppedReason = reason;
            LOG.warn("Evaluation provider calls stopped: reason={}, used={}, limit={}", reason, used, limit);
        }
    }

    synchronized boolean stopped() {
        return stoppedReason != null;
    }

    private ClientResponse observeResponse(ClientResponse response) {
        if (!stopOnFailure) return response;
        if (response.statusCode().isError())
            stopAfterFailure("http_" + response.statusCode().value());
        if (!response.headers()
                .contentType()
                .map(type -> type.isCompatibleWith(org.springframework.http.MediaType.TEXT_EVENT_STREAM))
                .orElse(false)) return response;
        AtomicBoolean done = new AtomicBoolean();
        StringBuilder tail = new StringBuilder("\n");
        return response.mutate()
                .body(body -> body.doOnNext(buffer -> {
                            tail.append(buffer.toString(java.nio.charset.StandardCharsets.UTF_8));
                            String text = tail.toString().replace("\r", "");
                            if (text.contains("\ndata: [DONE]\n")) done.set(true);
                            if (tail.length() > 32) tail.delete(0, tail.length() - 32);
                        })
                        .concatWith(Flux.defer(() -> {
                            if (done.get()) return Flux.empty();
                            stopAfterFailure("truncated_sse");
                            return Flux.error(new IllegalStateException("Provider SSE ended without DONE"));
                        }))
                        .doOnError(error -> stopAfterFailure("stream")))
                .build();
    }

    private static String origin(URI uri) {
        return uri.getScheme().toLowerCase(java.util.Locale.ROOT) + "://"
                + uri.getHost().toLowerCase(java.util.Locale.ROOT) + ":"
                + (uri.getPort() >= 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80));
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalStateException("Evaluation provider budget: " + message);
        }
    }

    @PreDestroy
    void close() {
        client.shutdownNow();
    }
}
