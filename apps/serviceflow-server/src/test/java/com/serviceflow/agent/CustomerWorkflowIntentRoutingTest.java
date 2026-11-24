package com.serviceflow.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.serviceflow.ai.AiGateway;
import com.serviceflow.ai.AiGateway.ProductReference;
import com.serviceflow.ai.AiGateway.ProductRouting;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CustomerWorkflowIntentRoutingTest {

    @Test
    void validStructuredChatDoesNotFallBackToKeywords() {
        AiGateway ai = mock(AiGateway.class);
        String question = "暂时不需要商品推荐，谢谢";
        var routing = new ProductRouting(Intent.CHAT, ProductReference.UNCLEAR, null);
        when(ai.classifyProductReference(question, "")).thenReturn(Optional.of(routing));

        assertThat(CustomerWorkflow.classifyIntent(ai, true, question, "")).isEqualTo(routing);
        verify(ai).classifyProductReference(question, "");
        verifyNoMoreInteractions(ai);
    }

    @Test
    void invalidStructuredOutputUsesOrdinaryIntent() {
        AiGateway ai = mock(AiGateway.class);
        String question = "这款的续航如何？";
        when(ai.classifyProductReference(question, "")).thenReturn(Optional.empty());
        when(ai.classify(question)).thenReturn(Optional.of(Intent.PRODUCT_QUERY));

        assertThat(CustomerWorkflow.classifyIntent(ai, true, question, ""))
                .isEqualTo(new ProductRouting(Intent.PRODUCT_QUERY, ProductReference.UNCLEAR, null));
        verify(ai).classify(question);
    }

    @Test
    void unavailableOrInvalidModelsUseRulesOnTheOriginalQuestion() {
        AiGateway ai = mock(AiGateway.class);
        String question = "保修政策是什么？";
        String context = "当前商品：某型号。\n";
        when(ai.classifyProductReference(question, context)).thenThrow(new IllegalStateException("unavailable"));
        when(ai.classify(context + "用户问题：" + question)).thenReturn(Optional.empty());

        assertThat(CustomerWorkflow.classifyIntent(ai, true, question, context))
                .isEqualTo(new ProductRouting(Intent.KNOWLEDGE_QUERY, ProductReference.UNCLEAR, null));
    }

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
