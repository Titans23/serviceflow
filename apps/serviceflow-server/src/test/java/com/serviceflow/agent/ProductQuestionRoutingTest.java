package com.serviceflow.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.serviceflow.model.ProductModels;
import com.serviceflow.rag.RagService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductQuestionRoutingTest {
    @Test
    void evidenceTraceContainsIdentifiersWithoutDocumentContents() {
        var evidence = new RagService.Evidence("chunk-one", "Title", "manual.md", "private source body", 0.9);
        var trace = CustomerWorkflow.evidenceTrace(new RagService.SearchResult(List.of(evidence), true, false));
        assertThat(trace).containsExactly(Map.of("chunkId", "chunk-one", "source", "manual.md"));
    }

    @Test
    void extractsCompleteModelNamesApartFromSkus() {
        assertThat(CustomerWorkflow.modelMentions("HUAWEI-PURA80-12-256 支持什么？")).isEmpty();
        assertThat(CustomerWorkflow.modelMentions("HUAWEI-PURA80-12-256 和 nova 14 Pro 比较"))
                .containsExactly("nova 14 Pro");
        assertThat(CustomerWorkflow.modelMentions("iPhone 16 Pro Max 的屏幕呢")).containsExactly("iPhone 16 Pro Max");
    }

    @Test
    void componentDifferencesDoNotCompareCapacityVariants() {
        assertThat(CustomerWorkflow.isProductComparison(
                        "这款折叠屏内外屏区别是什么？", List.of(product("PHONE-A-256", "Fold A"), product("PHONE-A-512", "Fold A"))))
                .isFalse();
        assertThat(CustomerWorkflow.isProductComparison("左右耳的功能区别？", List.of(product("EARBUD-A-ONE", "Clip A"))))
                .isFalse();
    }

    @Test
    void distinctModelsAndExplicitSkusStillSupportComparison() {
        assertThat(CustomerWorkflow.isProductComparison(
                        "比较这款和另一型号", List.of(product("PHONE-A-256", "Fold A"), product("PHONE-B-256", "Fold B"))))
                .isTrue();
        assertThat(CustomerWorkflow.isProductComparison(
                        "PHONE-A-256 和 PHONE-A-512 有什么区别？",
                        List.of(product("PHONE-A-256", "Fold A"), product("PHONE-A-512", "Fold A"))))
                .isTrue();
    }

    @Test
    void pageOperationsRouteToProductButPoliciesAndBusinessRemainSeparate() {
        for (String query : List.of("如何恢复出厂设置？", "蓝牙配对要怎么操作？", "如何重启？", "按键手势怎样设置？")) {
            assertThat(CustomerWorkflow.prioritizePageProduct(Intent.KNOWLEDGE_QUERY, 1, query))
                    .isEqualTo(Intent.PRODUCT_QUERY);
        }
        assertThat(CustomerWorkflow.prioritizePageProduct(Intent.KNOWLEDGE_QUERY, 1, "保修政策适用哪些地区？"))
                .isEqualTo(Intent.KNOWLEDGE_QUERY);
        assertThat(CustomerWorkflow.prioritizePageProduct(Intent.PRODUCT_QUERY, 1, "这款的保修政策是什么？"))
                .isEqualTo(Intent.KNOWLEDGE_QUERY);
        assertThat(CustomerWorkflow.prioritizePageProduct(Intent.ORDER_QUERY, 1, "查询订单"))
                .isEqualTo(Intent.ORDER_QUERY);
        assertThat(CustomerWorkflow.prioritizePageProduct(Intent.COMPLAINT, 1, "我要投诉按键失灵"))
                .isEqualTo(Intent.COMPLAINT);
        assertThat(CustomerWorkflow.prioritizePageProduct(Intent.KNOWLEDGE_QUERY, 0, "如何重置？"))
                .isEqualTo(Intent.KNOWLEDGE_QUERY);
    }

    private ProductModels.ProductView product(String sku, String model) {
        return new ProductModels.ProductView(
                1, sku, model, "brand", model, "phone", Map.of("storage", "256 GB"), null, "OFF_SALE");
    }
}
