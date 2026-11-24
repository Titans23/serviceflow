package com.serviceflow.ai;

import com.serviceflow.agent.Intent;
import com.serviceflow.model.OrderModels;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface AiGateway {
    default Optional<ProductRouting> classifyProductReference(String message, String routingContext) {
        return Optional.empty();
    }

    enum ProductReference {
        CURRENT,
        NEW,
        UNCLEAR
    }

    record ProductRouting(Intent intent, ProductReference reference, String mention) {}

    default Optional<OrderCommand> requestOrderTool(
            String message, LocalDate today, boolean hasCurrentOrder, List<OrderModels.Summary> candidates) {
        return Optional.empty();
    }

    sealed interface OrderCommand {
        record Search(OrderModels.Search filters) implements OrderCommand {}

        record ReadCurrent() implements OrderCommand {}

        record SelectCandidate(int index) implements OrderCommand {}

        record AskUser() implements OrderCommand {}
    }

    void streamAnswer(String systemPrompt, String userPrompt, Consumer<String> tokenConsumer);

    default boolean supportsGroundedGeneration() {
        return false;
    }

    default EvidenceEvaluation evaluateEvidence(String query, List<EvidenceCandidate> candidates) {
        return new EvidenceEvaluation(
                !candidates.isEmpty(),
                candidates.stream().limit(5).map(EvidenceCandidate::chunkId).toList());
    }

    default String rewriteQuery(String query) {
        return query;
    }

    record EvidenceCandidate(String chunkId, String title, String content) {}

    record EvidenceEvaluation(boolean sufficient, List<String> rankedChunkIds) {}
}
