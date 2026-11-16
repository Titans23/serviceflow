package com.serviceflow.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.ai.AiGateway;
import com.serviceflow.config.ServiceFlowProperties;
import com.serviceflow.model.ProductModels;
import com.serviceflow.rag.MilvusRagService;
import com.serviceflow.rag.RagService;
import com.serviceflow.service.ProductService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RagEvaluationControllerTest {
    @Test
    void usesRealEvidenceContractWithoutLeakingCatalogSpecificationsIntoAnswer() throws Exception {
        var rag = mock(MilvusRagService.class);
        var ai = mock(AiGateway.class);
        var products = mock(ProductService.class);
        var mapper = new ObjectMapper();
        var properties = new ServiceFlowProperties(
                null,
                null,
                null,
                null,
                new ServiceFlowProperties.Rag(
                        "cloud", "http://localhost", Duration.ofSeconds(1), Duration.ofSeconds(1), "fixture", 1));
        when(products.get(1L))
                .thenReturn(new ProductModels.ProductView(
                        1L,
                        "sku",
                        "phone",
                        "brand",
                        "model",
                        "phone",
                        Map.of("battery", "catalog-only-answer"),
                        BigDecimal.TEN,
                        "ON_SALE"));
        when(ai.supportsGroundedGeneration()).thenReturn(true);
        when(rag.search("question", "PRODUCT_MANUAL", List.of(1L), MilvusRagService.RetrievalMode.BM25))
                .thenReturn(new RagService.SearchResult(
                        List.of(new RagService.Evidence(
                                "chunk", "title", "source.md", "## battery：电池\n\nevidence answer", 1)),
                        true,
                        false,
                        "trace"));
        doAnswer(invocation -> {
                    assertThat(invocation.<String>getArgument(1))
                            .contains("evidence answer")
                            .doesNotContain("catalog-only-answer");
                    invocation.<Consumer<String>>getArgument(2).accept("{\"factIds\":[2],\"insufficient\":false}");
                    return null;
                })
                .when(ai)
                .streamAnswer(anyString(), anyString(), any());
        var controller = new RagEvaluationController(rag, ai, products, mapper, properties);
        var result = controller.evaluate(
                new RagEvaluationController.Request("question", 1L, MilvusRagService.RetrievalMode.BM25));
        assertThat(result.get("answer").toString()).contains("evidence answer").doesNotContain("catalog-only-answer");
    }

    @Test
    void evaluationEndpointIsAbsentFromOrdinaryApplication() {
        context().run(application -> assertThat(application).doesNotHaveBean(RagEvaluationController.class));
        context().withPropertyValues("serviceflow.evaluation.enabled=false").run(application -> assertThat(application)
                .doesNotHaveBean(RagEvaluationController.class));
        context().withPropertyValues("serviceflow.evaluation.enabled=true").run(application -> assertThat(application)
                .hasSingleBean(RagEvaluationController.class));
    }

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner()
                .withUserConfiguration(RagEvaluationController.class)
                .withBean(MilvusRagService.class, () -> mock(MilvusRagService.class))
                .withBean(AiGateway.class, () -> mock(AiGateway.class))
                .withBean(ProductService.class, () -> mock(ProductService.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(ServiceFlowProperties.class, () -> mock(ServiceFlowProperties.class));
    }
}
