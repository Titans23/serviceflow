package com.serviceflow.ai;

import com.serviceflow.agent.Intent;
import com.serviceflow.model.OrderModels;
import java.util.List;
import java.util.function.Consumer;

public interface AiGateway {
    Intent classify(String message);

    OrderPlan planOrder(
            String message,
            String today,
            boolean hasCurrentOrder,
            List<OrderModels.Summary> candidates,
            boolean pendingCancellation);

    enum OrderTool {
        SEARCH_ORDERS,
        READ_CURRENT,
        SELECT_CANDIDATE,
        ASK_USER
    }

    enum OrderPurpose {
        QUERY,
        CANCEL
    }

    record OrderPlan(
            OrderTool tool,
            OrderPurpose purpose,
            String keyword,
            String status,
            String fromDate,
            String toDate,
            Integer candidateIndex) {}

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
