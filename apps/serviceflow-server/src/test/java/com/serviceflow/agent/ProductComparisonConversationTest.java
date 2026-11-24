package com.serviceflow.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
