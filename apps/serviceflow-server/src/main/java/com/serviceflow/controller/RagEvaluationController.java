package com.serviceflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.ai.AiGateway;
import com.serviceflow.ai.GroundedAnswerComposer;
import com.serviceflow.config.ServiceFlowProperties;
import com.serviceflow.rag.MilvusRagService;
import com.serviceflow.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Admin-only, opt-in knowledge QA evaluation; no business mutations or gold labels. */
@RestController
@RequestMapping("/admin/evaluation/rag")
@ConditionalOnProperty(name = "serviceflow.evaluation.enabled", havingValue = "true")
public final class RagEvaluationController {
    private final MilvusRagService rag;
    private final AiGateway ai;
    private final ProductService products;
    private final ObjectMapper mapper;
    private final ServiceFlowProperties properties;

    public RagEvaluationController(
            MilvusRagService rag,
            AiGateway ai,
            ProductService products,
            ObjectMapper mapper,
            ServiceFlowProperties properties) {
        this.rag = rag;
        this.ai = ai;
        this.products = products;
        this.mapper = mapper;
        this.properties = properties;
    }

    public record Request(
            @NotBlank @Size(max = 4000) String question,
            @Positive long productId,
            @NotNull MilvusRagService.RetrievalMode mode) {}

    @PostMapping
    public Map<String, Object> evaluate(@Valid @RequestBody Request request) throws Exception {
        if (!"cloud".equalsIgnoreCase(properties.rag().mode()) || !ai.supportsGroundedGeneration()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Real cloud RAG required");
        }
        var product = products.get(request.productId());
        long started = System.nanoTime();
        var result = rag.search(request.question(), "PRODUCT_MANUAL", List.of(product.id()), request.mode());
        if (result.degraded() || result.diagnosticId() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Incomplete RAG execution or trace");
        }
        // Identity only: catalog specifications must not answer a retrieval benchmark for free.
        var context =
                GroundedAnswerComposer.context(Map.of("名称", product.name(), "SKU", product.sku()), result.evidence());
        String serialized = mapper.writeValueAsString(context);
        StringBuilder selection = new StringBuilder();
        ai.streamAnswer(
                GroundedAnswerComposer.SYSTEM_PROMPT,
                GroundedAnswerComposer.prompt(List.of(), request.question(), serialized),
                selection::append);
        String answer = GroundedAnswerComposer.compose(serialized, selection.toString(), mapper);
        return Map.of(
                "result",
                result,
                "answer",
                answer,
                "factContext",
                context,
                "selection",
                mapper.readTree(selection
                        .toString()
                        .replaceFirst("(?s)^\\s*```(?:json)?\\s*", "")
                        .replaceFirst("(?s)\\s*```\\s*$", "")
                        .strip()),
                "elapsedMs",
                (System.nanoTime() - started) / 1_000_000.0);
    }
}
