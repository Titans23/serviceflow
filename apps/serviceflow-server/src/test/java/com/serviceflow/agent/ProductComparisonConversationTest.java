package com.serviceflow.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.ai.AiGateway;
import com.serviceflow.ai.AiGateway.ProductReference;
import com.serviceflow.ai.AiGateway.ProductTarget;
import com.serviceflow.infrastructure.redis.ChatMemory;
import com.serviceflow.model.ChatModels.ComparisonSelection;
import com.serviceflow.model.ChatModels.PendingComparison;
import com.serviceflow.model.ProductModels;
import com.serviceflow.model.ProductModels.ProductView;
import com.serviceflow.rag.RerankerClient;
import com.serviceflow.security.CurrentPrincipal;
import com.serviceflow.service.ProductService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductComparisonConversationTest {
    private final ProductService products = mock(ProductService.class);
    private final ChatMemory memory = mock(ChatMemory.class);
    private final RerankerClient reranker = mock(RerankerClient.class);
    private final ProductReferenceResolver resolver =
            new ProductReferenceResolver(products, reranker, true, "cloud", .2);
    private final ProductComparisonConversation conversation =
            new ProductComparisonConversation(products, resolver, memory);
    private final AtomicReference<PendingComparison> stored = new AtomicReference<>();
    private final List<Map.Entry<String, Object>> events = new ArrayList<>();
    private final List<ProductView> fixtures =
            IntStream.rangeClosed(1, 10).mapToObj(this::phone).toList();

    @BeforeEach
    void setup() {
        for (var phone : fixtures) {
            when(products.resolve(phone.model())).thenReturn(List.of(phone));
            when(products.resolve(phone.sku())).thenReturn(List.of(phone));
            when(products.get(phone.id())).thenReturn(phone);
        }
        when(products.compareForChat(anyList())).thenAnswer(call -> {
            List<Long> ids = call.getArgument(0);
            return new ProductModels.Comparison(
                    "phone",
                    List.of("storage"),
                    ids.stream().map(id -> fixtures.get(id.intValue() - 1)).toList());
        });
        when(memory.pendingComparison("guest", "session")).thenAnswer(call -> stored.get());
        doAnswer(call -> {
                    stored.set(call.getArgument(2));
                    return null;
                })
                .when(memory)
                .putPendingComparison(eq("guest"), eq("session"), any());
        when(memory.updatePendingComparison(eq("guest"), eq("session"), any(), any()))
                .thenAnswer(call -> stored.compareAndSet(call.getArgument(2), call.getArgument(3)));
    }

    @Test
    void confirmsEightThenLetsUserCompareOrSupplementThroughTheSamePendingList() {
        var eight = fixtures.subList(0, 8).stream()
                .map(phone -> target(phone.model()))
                .toList();
        var initial = start(eight, 10, false);
        assertThat(initial.answer()).contains("已确认 8 款", "2 款待确认", "先比较");
        verify(products, never()).compareForChat(anyList());
        var id = stored.get().comparisonId();
        assertThat(stored.get().targets()).hasSize(10);

        var partial = continueWith(new ComparisonSelection(id, null, null, null, true));
        assertThat(partial.answer()).contains("本次比较已确认的 8 款");
        assertThat(partial.productIds()).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        assertThat(stored.get().comparisonId()).isEqualTo(id);

        // Complete the remaining positions out of order.
        assertThat(continueWith(new ComparisonSelection(
                                id, 9, null, fixtures.get(9).sku(), false))
                        .answer())
                .contains("已确认 9 款");
        var full = continueWith(
                new ComparisonSelection(id, 8, null, fixtures.get(8).model(), false));
        assertThat(full.productIds()).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        assertThat(events)
                .filteredOn(event -> event.getKey().equals("product_comparison"))
                .hasSize(2);
        verifyNoInteractions(reranker);
    }

    @Test
    void fallbackKeepsNamedAndMissingSlotsInTheSourceOrder() {
        String query = "之前聊的是 Galaxy S24，这次对比 3 款产品：Phone 2、缺失款、Phone 1";
        var targets = ProductReferenceRules.comparisonTargets(query);
        assertThat(targets).containsExactly(target("Phone 2"), target("缺失款"), target("Phone 1"));
        start(targets, ProductReferenceRules.expectedCount(query), false);

        assertThat(stored.get().targets().get(1).confirmed()).isNull();
        assertThat(continueWith(new ComparisonSelection(stored.get().comparisonId(), null, null, null, true))
                        .productIds())
                .containsExactly(2L, 1L);
        verify(products, never()).resolve("Galaxy S24");
        verifyNoInteractions(reranker);
    }

    @Test
    void completeRuleListCanRerankAliasesWithoutConfirmingThem() {
        String query = "对比这 5 款手机：Phone 1、苹果叫法、Phone 2、另一种叫法、Phone 5";
        AiGateway ai = mock(AiGateway.class);
        when(ai.classifyProductReference(query, "")).thenThrow(new IllegalStateException("classifier unavailable"));
        assertThat(CustomerWorkflow.classifyIntent(ai, query, "").targets()).isEmpty();
        var terms = ProductReferenceRules.comparisonTargets(query);
        int expected = ProductReferenceRules.expectedCount(query);
        when(products.search(null, null, 0, 50))
                .thenReturn(new ProductModels.ProductPage(fixtures, 0, 50, fixtures.size()));
        when(reranker.rankScored(eq("苹果叫法"), anyList(), eq(3)))
                .thenReturn(List.of(
                        new RerankerClient.ScoredDocument("3", .95), new RerankerClient.ScoredDocument("99", .90)));
        when(reranker.rankScored(eq("另一种叫法"), anyList(), eq(3)))
                .thenReturn(List.of(new RerankerClient.ScoredDocument("4", .94)));

        boolean complete = ProductReferenceRules.completeExplicitList(query, expected, terms);
        assertThat(complete).isTrue();
        var reply = start(terms, expected, complete);

        assertThat(reply.answer()).contains("已确认 3 款", "2 款待确认");
        var targets = stored.get().targets();
        assertThat(targets)
                .extracting(item -> item.label())
                .containsExactly("Phone 1", "苹果叫法", "Phone 2", "另一种叫法", "Phone 5");
        assertThat(targets.get(1).confirmed()).isNull();
        assertThat(targets.get(1).candidates()).extracting(item -> item.id()).containsExactly(3L);
        assertThat(targets.get(3).confirmed()).isNull();
        assertThat(targets.get(3).candidates()).extracting(item -> item.id()).containsExactly(4L);
    }

    @Test
    void onlyAnExplicitAndCompleteListQualifiesForRuleBasedSemanticFallback() {
        for (String query :
                List.of("对比这 5 款手机：苹果叫法、另一种叫法", "对比苹果叫法和另一种叫法", "苹果叫法和另外两款手机有什么区别？", "不用对比这 2 款手机：苹果叫法、另一种叫法")) {
            assertThat(ProductReferenceRules.completeExplicitList(
                            query,
                            ProductReferenceRules.expectedCount(query),
                            ProductReferenceRules.comparisonTargets(query)))
                    .as(query)
                    .isFalse();
        }
    }

    @Test
    void unavailableRerankerDoesNotBlockOtherConfirmedProducts() {
        when(products.resolve("Unknown")).thenReturn(List.of());
        when(products.search(null, null, 0, 50))
                .thenReturn(new ProductModels.ProductPage(fixtures, 0, 50, fixtures.size()));
        when(reranker.rankScored(eq("Unknown"), anyList(), eq(3))).thenThrow(new IllegalStateException());

        var reply = start(List.of(target("Phone 1"), target("Phone 2"), target("Unknown")), 3, true);
        assertThat(reply.degraded()).isTrue();
        assertThat(reply.answer()).contains("已确认 2 款", "1 款待确认");
        assertThat(stored.get().targets().get(2).confirmed()).isNull();
    }

    @Test
    void repeatingAnAppliedSelectionReturnsTheTableWithoutWritingAgain() {
        start(List.of(target("Phone 1")), 2, false);
        var selection = new ComparisonSelection(stored.get().comparisonId(), 1, null, "Phone 2", false);
        assertThat(continueWith(selection).productIds()).containsExactly(1L, 2L);
        assertThat(continueWith(selection).productIds()).containsExactly(1L, 2L);
        verify(memory).updatePendingComparison(eq("guest"), eq("session"), any(), any());
    }

    @Test
    void skippingAnItemKeepsPositionsAndIsSafeToRepeat() {
        start(List.of(target("Phone 1"), target("Unknown"), target("Phone 2")), 4, false);
        String id = stored.get().comparisonId();
        var skip = new ComparisonSelection(id, 1, null, null, false, true);
        assertThat(continueWith(skip).answer()).contains("已确认 2 款", "1 款待确认");
        assertThat(stored.get().targets().get(1).skipped()).isTrue();
        assertThat(continueWith(new ComparisonSelection(id, 1, null, "Phone 4", false))
                        .answer())
                .contains("已跳过");
        assertThat(continueWith(new ComparisonSelection(id, 3, null, "Phone 3", false))
                        .productIds())
                .containsExactly(1L, 2L, 3L);
        assertThat(continueWith(skip).productIds()).containsExactly(1L, 2L, 3L);
        verify(memory, times(2)).updatePendingComparison(eq("guest"), eq("session"), any(), any());
    }

    @Test
    void skippingRequiresAPendingTargetAndStillRequiresTwoProducts() {
        start(List.of(target("Phone 1"), target("Unknown")), 2, false);
        String id = stored.get().comparisonId();
        assertThat(continueWith(new ComparisonSelection(id, 0, null, null, false, true))
                        .answer())
                .contains("已经确认");
        assertThat(continueWith(new ComparisonSelection(id, 1, 2L, null, false, true))
                        .answer())
                .contains("请为一个待确认项");
        assertThat(continueWith(new ComparisonSelection(id, 1, null, null, false, true))
                        .answer())
                .contains("剩余商品不足 2 款");
        verify(products, never()).compareForChat(anyList());
    }

    @Test
    void existingComparisonSnapshotsStillRoundTripForRedisCompareAndSet() throws Exception {
        var mapper = new ObjectMapper();
        String legacy =
                "{\"comparisonId\":\"comparison\",\"targets\":[{\"label\":\"Phone\",\"confirmed\":null,\"candidates\":[]}]}";
        var snapshot = mapper.readValue(legacy, PendingComparison.class);

        assertThat(snapshot.targets().getFirst().skipped()).isFalse();
        assertThat(mapper.writeValueAsString(snapshot)).isEqualTo(legacy);
    }

    @Test
    void rejectsTooFewConfirmedProductsDuplicatesAndStaleSelections() {
        var initial = start(List.of(target("Phone 1"), target("Phone 1")), 2, false);
        assertThat(initial.answer()).contains("已确认 1 款", "至少确认 2 款");
        var id = stored.get().comparisonId();
        assertThat(stored.get().targets().get(1).label()).contains("重复");
        assertThat(continueWith(new ComparisonSelection(id, null, null, null, true))
                        .answer())
                .contains("至少确认 2 款");
        assertThat(continueWith(new ComparisonSelection(id, 1, 4L, null, false)).answer())
                .contains("不在");
        assertThat(continueWith(new ComparisonSelection("stale", 1, null, "Phone 2", false))
                        .answer())
                .contains("过期");
        verify(products, never()).compareForChat(anyList());
    }

    @Test
    void asksForFewerProductsWhenTheRequestedCountExceedsTen() {
        var reply = start(List.of(), 11, false);
        assertThat(reply.answer()).contains("最多比较 10 款");
        verify(memory).clearPendingComparison("guest", "session");
        verifyNoInteractions(products);
    }

    private CustomerWorkflow.Result start(List<ProductTarget> targets, int count, boolean semantic) {
        return conversation.start(
                state(), targets, count, semantic, (name, value) -> events.add(Map.entry(name, value)));
    }

    private CustomerWorkflow.Result continueWith(ComparisonSelection selection) {
        var state = state();
        state.comparisonSelection(selection);
        return conversation.continueComparison(state, (name, value) -> events.add(Map.entry(name, value)));
    }

    private ServiceFlowState state() {
        return new ServiceFlowState("session", new CurrentPrincipal("guest", "GUEST", null), "对比商品", null);
    }

    private ProductTarget target(String label) {
        return new ProductTarget(ProductReference.NEW, label);
    }

    private ProductView phone(int index) {
        return new ProductView(
                index,
                "SF-COMPARISON-" + index,
                "Phone " + index,
                "SF",
                "Phone " + index,
                "phone",
                Map.of(),
                null,
                "ON_SALE");
    }
}
