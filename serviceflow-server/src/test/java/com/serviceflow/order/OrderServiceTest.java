package com.serviceflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class OrderServiceTest {
    private OrderMapper mapper;
    private OrderService service;

    @BeforeEach
    void setUp() {
        mapper = mock(OrderMapper.class);
        service = new OrderService(mapper);
    }

    @Test
    void returnsExistingOperationWithoutRepeatingSideEffects() {
        var existing = new OrderModels.Operation("request-1", "CANCELLED", "已取消");
        when(mapper.findOperation("request-1")).thenReturn(existing);

        assertThat(service.cancel("SF-1001", 7L, "request-1")).isSameAs(existing);
        verify(mapper, never()).findOwned(anyString(), anyLong());
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

        assertThat(service.cancel("SF-1001", 7L, "request-2").resultCode()).isEqualTo("CANCELLED");
        verify(mapper).cancel(10L, 7L, 3);
        verify(mapper).startRefund(10L);
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
        when(mapper.findOperation("request-5")).thenReturn(null, reservedByOther);
        when(mapper.findOwned("SF-1001", 7L)).thenReturn(order("CREATED"));
        when(mapper.reserveOperation("request-5", 10L)).thenReturn(0);
        assertThat(service.cancel("SF-1001", 7L, "request-5")).isSameAs(reservedByOther);

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

    private OrderModels.Order order(String status) {
        return new OrderModels.Order(10L, "SF-1001", 7L, status, BigDecimal.valueOf(4999), 3, Instant.now());
    }
}
