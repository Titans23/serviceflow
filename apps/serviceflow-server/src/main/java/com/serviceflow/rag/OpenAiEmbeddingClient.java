package com.serviceflow.rag;

import com.serviceflow.ai.AiCallExecutor;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
import java.util.Objects;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "serviceflow.rag.mode", havingValue = "cloud")
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(20);
    private final EmbeddingModel model;
    private final AiCallExecutor calls;

    public OpenAiEmbeddingClient(EmbeddingModel model, AiCallExecutor calls) {
        this.model = Objects.requireNonNull(model, "model must not be null");
        this.calls = Objects.requireNonNull(calls, "calls must not be null");
    }

    @Override
    @Retry(name = "embedding")
    @CircuitBreaker(name = "embedding")
    @Bulkhead(name = "embedding", type = Bulkhead.Type.SEMAPHORE)
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Embedding text must not be blank");
        }

        return calls.execute("embedding", CALL_TIMEOUT, () -> model.embed(text));
    }
}
