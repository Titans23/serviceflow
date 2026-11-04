package com.serviceflow.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.serviceflow.mapper.OrderMapper;
import com.serviceflow.model.OrderModels;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class OrderServiceTest {
    private OrderMapper mapper;
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(OrderMapper.class);
        var refunds = mock(com.serviceflow.mapper.RefundMapper.class);
        when(refunds.enqueueDelivery(anyString())).thenReturn(1);
        service = new OrderServiceImpl(mapper, refunds);
    }

    @Test
    void returnsExistingOperationWithoutRepeatingSideEffects() {
        var existing = new OrderModels.Operation("request-1", "CANCELLED", "已取消");
        when(mapper.findOperation("request-1")).thenReturn(existing);
        when(mapper.findOwned("SF-1001", 7L)).thenReturn(order("CANCELLED"));
        when(mapper.findOwnedOperationOrderNo("request-1", 7L)).thenReturn("SF-1001");

        assertThat(service.cancel("SF-1001", 7L, "request-1")).isSameAs(existing);
        verify(mapper).findOwned("SF-1001", 7L);
        verify(mapper, never()).cancel(anyLong(), anyLong(), anyInt());
    }

    @Test
    void cancelsPaidOrderWithOptimisticVersionAndStartsRefund() {
        var order = order("PAID");
        var completed = new OrderModels.Operation("request-2", "CANCELLED", "订单已取消");
        when(mapper.findOperation("request-2")).thenReturn(null, completed);
        when(mapper.findOwned("SF-1001", 7L)).thenReturn(order);
        when(mapper.reserveOperation("request-2", 10L)).thenReturn(1);
        when(mapper.cancel(10L, 7L, 3)).thenReturn(1);
        when(mapper.startRefund(10L)).thenReturn(1);
        when(mapper.createRefund(10L, "request-2")).thenReturn(1);

        assertThat(service.cancel("SF-1001", 7L, "request-2").resultCode()).isEqualTo("CANCELLED");
        verify(mapper).cancel(10L, 7L, 3);
        verify(mapper).startRefund(10L);
        verify(mapper).createRefund(10L, "request-2");
    }

    @Test
    void rejectsShippedOrderAndDoesNotUpdateIt() {
        when(mapper.findOperation("request-3"))
                .thenReturn(null, new OrderModels.Operation("request-3", "NOT_CANCELLABLE", "请按退货政策处理"));
        when(mapper.findOwned("SF-1001", 7L)).thenReturn(order("SHIPPED"));
        when(mapper.reserveOperation("request-3", 10L)).thenReturn(1);

        assertThat(service.cancel("SF-1001", 7L, "request-3").resultCode()).isEqualTo("NOT_CANCELLABLE");
        verify(mapper, never()).cancel(anyLong(), anyLong(), anyInt());
    }

    @Test
    void reportsOptimisticLockConflict() {
        when(mapper.findOperation("request-4"))
                .thenReturn(null, new OrderModels.Operation("request-4", "CONFLICT", "订单状态已变化"));
        when(mapper.findOwned("SF-1001", 7L)).thenReturn(order("PROCESSING"));
        when(mapper.reserveOperation("request-4", 10L)).thenReturn(1);
        when(mapper.cancel(10L, 7L, 3)).thenReturn(0);

        assertThat(service.cancel("SF-1001", 7L, "request-4").resultCode()).isEqualTo("CONFLICT");
        verify(mapper, never()).startRefund(anyLong());
    }

    @Test
    void customerCannotReadAnotherCustomersOrder() {
        when(mapper.findOwned("SF-SECRET", 7L)).thenReturn(null);
        assertThatThrownBy(() -> service.get("SF-SECRET", 7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    void returnsOwnedOrderDetails() {
        var order = order("DELIVERED");
        when(mapper.findOwned("SF-1001", 7L)).thenReturn(order);
        var items = java.util.List.of(new OrderModels.Item("SKU-1", "Phone", 1, BigDecimal.valueOf(4999)));
        var payment = new OrderModels.Payment("PAID", null, java.time.Instant.now(), null);
        var shipment = java.util.List.of(new OrderModels.Shipment("签收", "上海", java.time.Instant.now()));
        when(mapper.items(10L)).thenReturn(items);
        when(mapper.payment(10L)).thenReturn(payment);
        when(mapper.shipment(10L)).thenReturn(shipment);

        var view = service.get("SF-1001", 7L);

        assertThat(view.status()).isEqualTo("DELIVERED");
        assertThat(view.items()).containsExactlyElementsOf(items);
        assertThat(view.payment()).isSameAs(payment);
        assertThat(view.shipment()).containsExactlyElementsOf(shipment);
    }

    @Test
    void handlesOperationReservationRaceAndCancelEligibility() {
        var reservedByOther = new OrderModels.Operation("request-5", "CANCELLED", "已取消");
        when(mapper.findOperation("request-5")).thenReturn(null);
        when(mapper.findLatestOperation("request-5", 10L)).thenReturn(reservedByOther);
        when(mapper.findOwned("SF-1001", 7L)).thenReturn(order("CREATED"));
        when(mapper.reserveOperation("request-5", 10L)).thenReturn(0);
        assertThat(service.cancel("SF-1001", 7L, "request-5")).isSameAs(reservedByOther);
        verify(mapper).findLatestOperation("request-5", 10L);
        verify(mapper, never()).cancel(anyLong(), anyLong(), anyInt());
        verify(mapper, never()).startRefund(anyLong());

        when(mapper.findOwned("SF-1001", 7L)).thenReturn(order("PAID"));
        assertThat(service.canCancel("SF-1001", 7L)).isTrue();
        when(mapper.findOwned("SF-1001", 7L)).thenReturn(order("DELIVERED"));
        assertThat(service.canCancel("SF-1001", 7L)).isFalse();
    }

    @Test
    void rejectsCancelWhenOrderIsNotOwned() {
        when(mapper.findOperation("request-6")).thenReturn(null);
        when(mapper.findOwned("SF-404", 7L)).thenReturn(null);
        assertThatThrownBy(() -> service.cancel("SF-404", 7L, "request-6"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    void unpaidCancellationCreatesNoRefund() {
        when(mapper.findOwned("SF-1001", 7L)).thenReturn(order("CREATED"));
        when(mapper.reserveOperation("unpaid", 10L)).thenReturn(1);
        when(mapper.cancel(10L, 7L, 3)).thenReturn(1);
        when(mapper.payment(10L)).thenReturn(new OrderModels.Payment("PENDING", "NONE", null, null));

        service.cancel("SF-1001", 7L, "unpaid");

        verify(mapper, never()).createRefund(anyLong(), anyString());
        verify(mapper).completeOperation("unpaid", "CANCELLED", "订单已取消，无需退款。");
    }

    @Test
    void missingRefundRecordMustFailCancellationTransaction() {
        when(mapper.findOwned("SF-1001", 7L)).thenReturn(order("PAID"));
        when(mapper.reserveOperation("missing-refund", 10L)).thenReturn(1);
        when(mapper.cancel(10L, 7L, 3)).thenReturn(1);
        when(mapper.startRefund(10L)).thenReturn(1);

        assertThatThrownBy(() -> service.cancel("SF-1001", 7L, "missing-refund"))
                .isInstanceOf(IllegalStateException.class);
        verify(mapper, never()).completeOperation(anyString(), anyString(), anyString());
    }

    @Test
    void inconsistentPaidRefundStateMustFailCancellationTransaction() {
        when(mapper.findOwned("SF-1001", 7L)).thenReturn(order("PAID"));
        when(mapper.reserveOperation("bad-payment", 10L)).thenReturn(1);
        when(mapper.cancel(10L, 7L, 3)).thenReturn(1);
        when(mapper.payment(10L)).thenReturn(new OrderModels.Payment("PAID", "PROCESSING", Instant.now(), null));

        assertThatThrownBy(() -> service.cancel("SF-1001", 7L, "bad-payment"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("支付退款状态异常");
        verify(mapper, never()).completeOperation(anyString(), anyString(), anyString());
    }

    @Test
    void existingKeyCannotBypassCustomerOrTargetBinding() {
        var existing = new OrderModels.Operation("bound-key", "CANCELLED", "已取消");
        when(mapper.findOperation("bound-key")).thenReturn(existing);
        when(mapper.findOwned("SF-1001", 7L)).thenReturn(order("PAID"));
        when(mapper.findOwnedOperationOrderNo("bound-key", 7L)).thenReturn("ANOTHER-ORDER");
        assertThatThrownBy(() -> service.cancel("SF-1001", 7L, "bound-key"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.cancel("SF-1001", 8L, "bound-key"))
                .isInstanceOf(ResponseStatusException.class);
        verify(mapper, never()).reserveOperation(anyString(), anyLong());
        verify(mapper, never()).cancel(anyLong(), anyLong(), anyInt());
    }

    @Test
    void reservationCollisionForAnotherOrderCannotReplayItsResult() {
        when(mapper.findOwned("SF-1001", 7L)).thenReturn(order("PAID"));
        when(mapper.reserveOperation("collision", 10L)).thenReturn(0);
        when(mapper.findLatestOperation("collision", 10L)).thenReturn(null);
        assertThatThrownBy(() -> service.cancel("SF-1001", 7L, "collision"))
                .isInstanceOf(ResponseStatusException.class);
        verify(mapper, never()).cancel(anyLong(), anyLong(), anyInt());
    }

    @Test
    void recoveryReadsCommittedResultWithoutWritingOrConfirming() {
        when(mapper.findOwnedOperationOrderNo("completed", 7L)).thenReturn("SF-1001");
        when(mapper.findOperation("completed")).thenReturn(new OrderModels.Operation("completed", "CANCELLED", "已取消"));
        when(mapper.findOwned("SF-1001", 7L)).thenReturn(order("CANCELLED"));
        assertThat(service.cancellationResult("completed", 7L).order().status()).isEqualTo("CANCELLED");
        assertThatThrownBy(() -> service.cancellationResult("missing", 7L)).isInstanceOf(ResponseStatusException.class);
        verify(mapper, never()).reserveOperation(anyString(), anyLong());
        verify(mapper, never()).cancel(anyLong(), anyLong(), anyInt());
    }

    @Test
    void listsOwnedOrdersWithOneBatchOfItemsAndAnExtraRowForTheCursor() {
        var first = order("PAID");
        var next = new OrderModels.Order(9L, "SF-1002", 7L, "PAID", BigDecimal.ONE, 0, Instant.now());
        when(mapper.searchOwned(eq(7L), eq("耳机"), eq("headphone"), eq("PAID"), any(), any(), isNull(), eq(2), isNull()))
                .thenReturn(List.of(first, next));
        when(mapper.itemsForOrders(List.of(10L)))
                .thenReturn(List.of(new OrderModels.ItemRow(10L, "SKU", "耳机", 1, BigDecimal.ONE)));
        var date = LocalDate.of(2026, 11, 3);
        var page = service.search(7L, new OrderModels.Search(" 耳机 ", "PAID", date, date), null, 1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().items()).hasSize(1);
        assertThat(page.nextCursor()).isEqualTo(10L);
        verify(mapper)
                .searchOwned(
                        7L,
                        "耳机",
                        "headphone",
                        "PAID",
                        Instant.parse("2026-11-02T16:00:00Z"),
                        Instant.parse("2026-11-03T16:00:00Z"),
                        null,
                        2,
                        null);
        verify(mapper, never()).items(anyLong());
    }

    @Test
    void supportsEmptyResultsAndOwnedCursor() {
        var query = new OrderModels.Search(" ", "", null, null);
        when(mapper.searchOwned(eq(7L), isNull(), isNull(), isNull(), isNull(), isNull(), any(), eq(11), isNull()))
                .thenReturn(List.of());
        when(mapper.findOwnedById(10L, 7L)).thenReturn(order("PAID"));
        assertThat(service.search(7L, query, 10L, 10).items()).isEmpty();
        verify(mapper, never()).itemsForOrders(any());
        assertThatThrownBy(() -> service.search(8L, query, 10L, 10)).isInstanceOf(ResponseStatusException.class);
        when(mapper.searchOwned(eq(7L), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(11), isNull()))
                .thenReturn(List.of(order("PAID")));
        when(mapper.itemsForOrders(any())).thenReturn(List.of());
        assertThat(service.search(7L, query, null, 10).nextCursor()).isNull();
    }

    @Test
    void rejectsInvalidFiltersBeforeQuerying() {
        var date = LocalDate.of(2026, 11, 3);
        for (var query : List.of(
                new OrderModels.Search("x".repeat(81), null, null, null),
                new OrderModels.Search(null, null, null, null, "x".repeat(65)),
                new OrderModels.Search(null, "invalid", null, null),
                new OrderModels.Search(null, null, date, date.minusDays(1)),
                new OrderModels.Search(null, null, LocalDate.of(1999, 1, 1), null),
                new OrderModels.Search(null, null, LocalDate.of(2101, 1, 1), null),
                new OrderModels.Search(null, null, null, LocalDate.of(1999, 1, 1)),
                new OrderModels.Search(null, null, null, LocalDate.of(2101, 1, 1)))) {
            assertThatThrownBy(() -> service.search(7L, query, null, 10)).isInstanceOf(ResponseStatusException.class);
        }
        var empty = new OrderModels.Search(null, null, null, null);
        assertThatThrownBy(() -> service.search(7L, empty, null, 0)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.search(7L, empty, null, 51)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(mapper);
    }

    private OrderModels.Order order(String status) {
        return new OrderModels.Order(10L, "SF-1001", 7L, status, BigDecimal.valueOf(4999), 3, Instant.now());
    }
}
