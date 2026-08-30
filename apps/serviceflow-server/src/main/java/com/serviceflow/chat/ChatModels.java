package com.serviceflow.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;

public final class ChatModels {
    private ChatModels() {}

    public record PageContext(Long productId) {}

    public record MessageRequest(
            @NotBlank String message,
            @NotNull
                    @Pattern(
                            regexp =
                                    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
                    String clientRequestId,
            PageContext pageContext) {}

    public record Session(String publicId, String title, Instant createdAt, Instant updatedAt) {}

    public record Message(
            String publicId, String role, String content, String intent, String metadataJson, Instant createdAt) {}

    public record CreateSessionRequest(String title) {}

    public record ConfirmRequest(@NotBlank String decision) {}

    public record SseMeta(String intent, boolean degraded, List<String> citations) {}
}
