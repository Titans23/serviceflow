package com.serviceflow.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;

public final class ChatModels {
    private ChatModels() {}

    public record PageContext(Long productId, String orderNo) {
        public PageContext(Long productId) {
            this(productId, null);
        }
    }

    public record OrderSelection(@NotBlank String queryId, @NotBlank String orderNo) {}

    public record ComparisonSelection(
            @NotBlank @jakarta.validation.constraints.Size(max = 36) String comparisonId,
            @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(9) Integer targetIndex,
            @jakarta.validation.constraints.Positive Long productId,
            @jakarta.validation.constraints.Size(max = 120) String term,
            boolean compareConfirmed) {}

    public record ComparisonProduct(long id, String name, String sku) {}

    public record ComparisonTarget(String label, ComparisonProduct confirmed, List<ComparisonProduct> candidates) {}

    public record PendingComparison(String comparisonId, List<ComparisonTarget> targets) {}

    public record MessageRequest(
            @NotBlank String message,
            @NotNull
                    @Pattern(
                            regexp =
                                    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
                    String clientRequestId,
            PageContext pageContext,
            @jakarta.validation.Valid OrderSelection orderSelection,
            @jakarta.validation.constraints.Positive Long selectedProductId,
            @jakarta.validation.Valid ComparisonSelection comparisonSelection) {
        public MessageRequest(
                String message,
                String clientRequestId,
                PageContext pageContext,
                OrderSelection orderSelection,
                Long selectedProductId) {
            this(message, clientRequestId, pageContext, orderSelection, selectedProductId, null);
        }

        public MessageRequest(
                String message, String clientRequestId, PageContext pageContext, OrderSelection orderSelection) {
            this(message, clientRequestId, pageContext, orderSelection, null);
        }

        public MessageRequest(String message, String clientRequestId, PageContext pageContext) {
            this(message, clientRequestId, pageContext, null);
        }
    }

    public record Session(String publicId, String title, Instant createdAt, Instant updatedAt) {}

    public record Message(
            String publicId, String role, String content, String intent, String metadataJson, Instant createdAt) {}

    public record CreateSessionRequest(String title) {}

    public record ConfirmRequest(@NotBlank @Pattern(regexp = "CONFIRM|REJECT") String decision) {}
}
