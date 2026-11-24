package com.serviceflow.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.serviceflow.ai.AiGateway;
import com.serviceflow.ai.AiGateway.OrderCommand;
import com.serviceflow.infrastructure.redis.ChatMemory;
import com.serviceflow.model.ChatActionModels;
import com.serviceflow.model.ChatModels;
import com.serviceflow.model.OrderModels;
import com.serviceflow.security.CurrentPrincipal;
import com.serviceflow.service.ActionDecisionService;
import com.serviceflow.service.OrderService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class OrderConversationTest {
    private final AiGateway ai = mock(AiGateway.class);
    private final OrderService orders = mock(OrderService.class);
    private final ChatMemory memory = mock(ChatMemory.class);
    private final ActionDecisionService actions = mock(ActionDecisionService.class);
    private final OrderConversation conversation = new OrderConversation(ai, orders, memory, actions);
    private final AtomicReference<ChatMemory.OrderCandidates> pending = new AtomicReference<>();
    private final AtomicReference<String> active = new AtomicReference<>();
    private final List<Map.Entry<String, Object>> events = new ArrayList<>();
    private static final String FIRST = "SF202611030001", SECOND = "SF202611030002";

    @BeforeEach
    void setup() {
        when(memory.orderCandidates("owner", "session")).thenAnswer(call -> pending.get());
        when(memory.activeOrder("owner", "session")).thenAnswer(call -> active.get());
        doAnswer(call -> {
                    pending.set(call.getArgument(2));
                    return null;
                })
                .when(memory)
                .putOrderCandidates(eq("owner"), eq("session"), any());
        doAnswer(call -> {
                    pending.set(null);
                    return null;
                })
                .when(memory)
                .clearOrderCandidates("owner", "session");
        doAnswer(call -> {
                    active.set(call.getArgument(2));
                    return null;
                })
                .when(memory)
                .setActiveOrder(eq("owner"), eq("session"), anyString());
        doAnswer(call -> {
                    active.set(null);
                    return null;
                })
                .when(memory)
                .clearActiveOrder("owner", "session");
        when(orders.get(anyString(), eq(7L))).thenAnswer(call -> view(call.getArgument(0)));
        when(orders.canCancel(anyString(), eq(7L))).thenReturn(true);
        when(actions.prepare(any(), any())).thenReturn("pending-action");
    }

    @Test
    void searchesWithModelFiltersThenAsksAndRemembersCancellationAcrossSelection() {
        plan(new OrderCommand.Search(
                new OrderModels.Search("耳机", null, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3))));
        when(orders.search(eq(7L), any(), isNull(), eq(10)))
                .thenReturn(new OrderModels.Page(List.of(summary(FIRST), summary(SECOND)), null));
        assertThat(run(state("昨天买的耳机不要了")).answer()).contains("多笔");
        verify(orders)
                .search(
                        7L,
                        new OrderModels.Search(
                                "耳机", null, java.time.LocalDate.of(2026, 11, 3), java.time.LocalDate.of(2026, 11, 3)),
                        null,
                        10);
        verify(actions, never()).prepare(any(), any());
        assertThat(pending.get().cancellation()).isTrue();
        assertThat(run(state("第二笔")).answer()).contains("二次确认");
        var capture = org.mockito.ArgumentCaptor.forClass(ChatActionModels.Pending.class);
        verify(actions).prepare(capture.capture(), any());
        assertThat(capture.getValue().orderNo()).isEqualTo(SECOND);
        var card = events.stream()
                .filter(event -> event.getKey().equals("action_required"))
                .findFirst()
                .orElseThrow();
        assertThat(String.valueOf(((Map<?, ?>) card.getValue()).get("message"))).contains("无线耳机", SECOND, "299");
        assertThat(active.get()).isEqualTo(SECOND);
        assertThat(pending.get()).isNull();
        verify(orders, never()).cancel(anyString(), anyLong(), anyString());
        assertThat(run(state("它的物流呢")).answer()).contains(SECOND);
    }

    @Test
    void validatesCandidateCardQueryIdAndMembershipBeforeSelecting() {
        pending.set(new ChatMemory.OrderCandidates("latest", List.of(summary(FIRST), summary(SECOND)), false, false));
        var stale = state("选择订单");
        stale.orderSelection(new ChatModels.OrderSelection("old", FIRST));
        assertThat(run(stale).answer()).contains("过期");
        var forged = state("选择订单");
        forged.orderSelection(new ChatModels.OrderSelection("latest", "SF999999999999"));
        assertThat(run(forged).answer()).contains("过期");
        verifyNoInteractions(orders);
        var valid = state("选择订单");
        valid.orderSelection(new ChatModels.OrderSelection("latest", SECOND));
        assertThat(run(valid).answer()).contains(SECOND);
        verify(orders).get(SECOND, 7L);
    }

    @Test
    void rejectsForeignPageContextBeforePlanningAndNeverCachesIt() {
        var request = state("查一下");
        request.activeOrderNo("SF999999999999");
        when(orders.get("SF999999999999", 7L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> run(request)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(ai);
        verify(memory, never()).setActiveOrder(anyString(), anyString(), anyString());
    }

    @Test
    void explicitOrderSwitchRechecksOwnershipAndDoesNotInheritCancellation() {
        active.set(FIRST);
        pending.set(new ChatMemory.OrderCandidates("latest", List.of(summary(FIRST)), false, true));
        assertThat(run(state("查询 " + SECOND)).answer()).contains(SECOND);
        assertThat(active.get()).isEqualTo(SECOND);
        verify(actions, never()).prepare(any(), any());
        assertThat(run(state("不要取消 " + FIRST)).answer()).contains(FIRST);
        verify(actions, never()).prepare(any(), any());
    }

    @Test
    void emptyNewSearchClearsOldOrderAndDoesNotFallBackToIt() {
        active.set(FIRST);
        plan(new OrderCommand.Search(new OrderModels.Search("商品", null, null, null)));
        when(orders.search(eq(7L), any(), isNull(), eq(10))).thenReturn(new OrderModels.Page(List.of(), null));
        assertThat(run(state("查另一个商品")).answer()).contains("没有找到");
        assertThat(active.get()).isNull();
        assertThat(events).anyMatch(event -> event.getKey().equals("order_selection_required"));
        plan(new OrderCommand.ReadCurrent());
        assertThat(run(state("取消它")).answer()).contains("告诉我");
        verify(actions, never()).prepare(any(), any());
    }

    @Test
    void outOfRangeSelectionDoesNotChooseAnOrder() {
        pending.set(new ChatMemory.OrderCandidates("latest", List.of(summary(FIRST)), false, true));
        assertThat(run(state("第二笔")).answer()).contains("不能确定");
        verify(orders, never()).search(anyLong(), any(), any(), anyInt());
        verify(actions, never()).prepare(any(), any());
    }

    @Test
    void modelCanSelectOnlyFromTheCurrentCandidatesAndCanChangeCancelToQuery() {
        pending.set(new ChatMemory.OrderCandidates("latest", List.of(summary(FIRST), summary(SECOND)), false, true));
        plan(new OrderCommand.SelectCandidate(2));
        assertThat(run(state("先别取消，查便宜的那笔")).answer()).contains(SECOND);
        verify(actions, never()).prepare(any(), any());
    }

    @Test
    void shippedOrdersAndGuestsNeverGetACancellationAction() {
        when(orders.canCancel(FIRST, 7L)).thenReturn(false);
        assertThat(run(state("取消 " + FIRST)).answer()).contains("不能直接取消");
        var guest = new ServiceFlowState("guest", new CurrentPrincipal("guest", "GUEST", null), "查订单", null);
        assertThat(run(guest).answer()).contains("登录");
        verify(actions, never()).prepare(any(), any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {
                "不用取消，帮我查物流",
                "取消了吗",
                "取消了没",
                "是否已经取消",
                "不用帮我取消",
                "不想取消",
                "先不取消",
                "查看取消进度",
                "能不能取消",
                "怎么取消",
                "如果取消会怎么样",
                "只查退款状态",
                "取消成功了吗",
                "上次说取消这笔订单",
                "之前说过取消，现在需要发票",
                "取消过的那笔",
                "查询退款工单"
            })
    void readOnlyRequestsNeverCreateActionsWhenPlannerReadsAnOrder(String query) {
        assertThat(run(state(FIRST + " " + query)).answer()).contains(FIRST);
        active.set(FIRST);
        plan(new OrderCommand.ReadCurrent());
        assertThat(run(state(query)).answer()).contains(FIRST);
        verify(actions, never()).prepare(any(), any());
    }

    @Test
    void withdrawingCancellationClearsCandidateIntentBeforeLaterSelection() {
        pending.set(new ChatMemory.OrderCandidates("latest", List.of(summary(FIRST), summary(SECOND)), false, true));
        plan(new OrderCommand.AskUser());
        run(state("不用取消，先查状态"));
        assertThat(pending.get().cancellation()).isFalse();
        run(state("第二笔"));
        verify(actions, never()).prepare(any(), any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"请取消", "帮我取消这笔订单", "这个商品不要了"})
    void explicitRequestsStillRequireConfirmation(String query) {
        assertThat(run(state(query + " " + FIRST)).answer()).contains("二次确认");
        verify(actions).prepare(any(), any());
        verify(orders, never()).cancel(anyString(), anyLong(), anyString());
    }

    @Test
    void modelFailureFallsBackToGroundedFiltersAndKeepsCancellationPending() {
        when(ai.requestOrderTool(anyString(), any(), anyBoolean(), anyList()))
                .thenThrow(new IllegalStateException("unavailable"));
        when(orders.search(eq(7L), any(), isNull(), eq(10)))
                .thenReturn(new OrderModels.Page(List.of(summary(FIRST), summary(SECOND)), null));
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);

        assertThat(run(state("昨天买的耳机不要了")).answer()).contains("多笔");
        verify(orders).search(7L, new OrderModels.Search("耳机", null, yesterday, yesterday), null, 10);
        assertThat(pending.get().cancellation()).isTrue();
        assertThat(run(state("查上个月买的耳机")).answer()).contains("不能确定");
        assertThat(run(state("查还没发货的订单")).answer()).contains("明确");
        verify(orders, times(1)).search(anyLong(), any(), any(), anyInt());
        verify(actions, never()).prepare(any(), any());
    }

    @Test
    void dataAccessFailurePreservesTheCurrentCandidateList() {
        pending.set(new ChatMemory.OrderCandidates("latest", List.of(summary(FIRST)), false, false));
        when(orders.search(eq(7L), any(), isNull(), eq(10)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("temporary"));

        assertThat(run(state("我的订单")).answer()).contains("暂时不可用");
        assertThat(pending.get().queryId()).isEqualTo("latest");
    }

    @Test
    void fallsBackWhenTheCancellationVerbFollowsTheOrderDescription() {
        when(orders.search(eq(7L), any(), isNull(), eq(10)))
                .thenReturn(new OrderModels.Page(List.of(summary(FIRST), summary(SECOND)), null));
        assertThat(run(state("我的手机订单帮我取消")).answer()).contains("多笔");
        verify(orders).search(7L, new OrderModels.Search("手机", null, null, null), null, 10);
        assertThat(pending.get().cancellation()).isTrue();
        verify(actions, never()).prepare(any(), any());
    }

    private void plan(OrderCommand command) {
        when(ai.requestOrderTool(anyString(), any(), anyBoolean(), anyList())).thenReturn(Optional.of(command));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {
                "查询订单状态", "订单状态", "查物流", "查询物流", "它的物流呢", "退款进度", "查询退款进度", "退货进度", "查询退货进度", "工单进度", "查询退款工单", "退款工单结果"
            })
    void boundedCurrentOrderLookupsNeedNoModelAndNeverPrepareActions(String query) {
        active.set(FIRST);
        assertThat(run(state(query)).answer()).contains(FIRST);
        verifyNoInteractions(ai, actions);
        verify(orders).get(FIRST, 7L);
    }

    @Test
    void shortcutNeverMatchesComplaintOrMutationOrAmbiguousOrders() {
        assertThat(OrderConversation.isExactLookup("查询订单 " + FIRST)).isTrue();
        for (String query : List.of("投诉 " + FIRST, "取消 " + FIRST, FIRST + " " + SECOND, "这个订单能退货吗")) {
            assertThat(OrderConversation.isExactLookup(query)).isFalse();
            assertThat(OrderConversation.isCurrentLookup(query)).isFalse();
        }
    }

    private ServiceFlowState state(String query) {
        return new ServiceFlowState("session", new CurrentPrincipal("owner", "CUSTOMER", 7L), query, null);
    }

    private CustomerWorkflow.Result run(ServiceFlowState state) {
        return conversation.respond(state, (name, value) -> events.add(Map.entry(name, value)));
    }

    private OrderModels.Summary summary(String orderNo) {
        return new OrderModels.Summary(
                orderNo,
                "PAID",
                BigDecimal.valueOf(299),
                Instant.parse("2026-11-03T01:00:00Z"),
                List.of(new OrderModels.Item("SKU", "无线耳机", 1, BigDecimal.valueOf(299))));
    }

    private OrderModels.OrderView view(String orderNo) {
        return new OrderModels.OrderView(
                orderNo,
                "PAID",
                BigDecimal.valueOf(299),
                0,
                Instant.now(),
                List.of(new OrderModels.Item("SKU", "无线耳机", 1, BigDecimal.valueOf(299))),
                new OrderModels.Payment("PAID", "NONE", null, null),
                List.of(),
                null,
                null,
                null);
    }
}
