package com.serviceflow.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CustomerWorkflowIntentRoutingTest {

    @Test
    void extractsRealWorldModelsWithSpacesAndSuffixes() {
        assertThat(CustomerWorkflow.modelMentions("这款和 HUAWEI Pura 80 Pro、Pura 80 Ultra 有什么区别？"))
                .containsExactly("HUAWEI Pura 80 Pro", "Pura 80 Ultra");
    }

    @Test
    void extractsSkuEmbeddedInNaturalLanguage() {
        assertThat(CustomerWorkflow.skuMentions("请查询 HUAWEI-PURA80-12-256 的屏幕参数"))
                .containsExactly("HUAWEI-PURA80-12-256");
    }

    @Test
    void pageProductReferenceOverridesKnowledgeClassification() {
        Intent result = CustomerWorkflow.prioritizePageProduct(Intent.KNOWLEDGE_QUERY, 2L, "这款手机充电和防水需要注意什么？");

        assertThat(result).isEqualTo(Intent.PRODUCT_QUERY);
    }

    @Test
    void pageContextDoesNotOverrideOrderOrComplaint() {
        assertThat(CustomerWorkflow.prioritizePageProduct(Intent.ORDER_QUERY, 2L, "这个订单到哪了？"))
                .isEqualTo(Intent.ORDER_QUERY);
        assertThat(CustomerWorkflow.prioritizePageProduct(Intent.COMPLAINT, 2L, "我要投诉这款商品"))
                .isEqualTo(Intent.COMPLAINT);
    }

    @Test
    void unrelatedKnowledgeQuestionKeepsItsClassification() {
        Intent result = CustomerWorkflow.prioritizePageProduct(Intent.KNOWLEDGE_QUERY, 2L, "七天无理由退货政策是什么？");

        assertThat(result).isEqualTo(Intent.KNOWLEDGE_QUERY);
    }
}
