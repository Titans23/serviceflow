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
    void explicitSkuAndModelSwitchCannotReusePreviousProduct() {
        var old = product("HUAWEI-PURA80-12-256", "Pura 80");
        assertThat(CustomerWorkflow.retainsRememberedProduct("改问 HUAWEI-NOVA14PRO-256 的充电", old, List.of()))
                .isFalse();
        assertThat(CustomerWorkflow.retainsRememberedProduct("UNKNOWN-PHONE-999 是否支持？", old, List.of()))
                .isFalse();
        assertThat(CustomerWorkflow.retainsRememberedProduct(
                        "改问 nova 14 Pro", old, List.of(product("HUAWEI-NOVA14PRO-256", "nova 14 Pro"))))
                .isFalse();
        assertThat(CustomerWorkflow.retainsRememberedProduct("它支持无线充电吗？", old, List.of()))
                .isTrue();
        assertThat(CustomerWorkflow.retainsRememberedProduct("Pura 80 的屏幕呢？", old, List.of(old)))
                .isTrue();
        assertThat(CustomerWorkflow.modelMentions("HUAWEI-PURA80-12-256 支持什么？")).isEmpty();
        assertThat(CustomerWorkflow.modelMentions("HUAWEI-PURA80-12-256 和 nova 14 Pro 比较"))
                .containsExactly("nova 14 Pro");
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
