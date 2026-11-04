package com.serviceflow.service.impl;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.serviceflow.infrastructure.messaging.RefundChannelClient;
import com.serviceflow.infrastructure.messaging.RefundDispatcher;
import com.serviceflow.mapper.RefundDeliveryMapper;
import com.serviceflow.model.RefundDeliveryModels;
import com.serviceflow.model.RefundModels;
import com.serviceflow.service.RefundDeliveryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RefundDispatcherTest {
    private final RefundDeliveryService delivery = mock(RefundDeliveryService.class);
    private final RefundChannelClient channel = mock(RefundChannelClient.class);
    private final RefundDispatcher worker = new RefundDispatcher(
            mock(RefundDeliveryMapper.class), delivery, channel, mock(RabbitTemplate.class), new SimpleMeterRegistry());

    @Test
    void unknownQueryCannotAuthorizeSubmission() {
        var claim = claim(Instant.now().plusSeconds(60));
        when(delivery.claim(1)).thenReturn(claim);
        when(channel.query("id")).thenThrow(new IllegalStateException("unavailable"));
        worker.process("1");
        verify(channel, never()).submit(any());
        verify(delivery).waitOrEscalate(eq(claim), contains("未知"));
    }

    @Test
    void responseLossIsResolvedByQueryingTheSameRefundWithoutSubmittingAgain() {
        var claim = claim(Instant.now().plusSeconds(60));
        when(delivery.claim(1)).thenReturn(claim);
        var success = new RefundDeliveryModels.ChannelResult("id", 1, "SUCCEEDED", BigDecimal.TEN, "");
        when(channel.query("id")).thenReturn(null, success);
        when(delivery.beforeSubmit(claim)).thenReturn(true);
        when(channel.submit(claim.refund())).thenThrow(new IllegalStateException("lost response"));
        worker.process("1");
        worker.process("1");
        verify(channel, times(1)).submit(claim.refund());
        verify(delivery).accept(claim, success);
    }

    @Test
    void exhaustedDeadlineAndLostLeaseCannotStartANewSubmission() {
        var expired = claim(Instant.now().minusSeconds(1));
        when(delivery.claim(1)).thenReturn(expired);
        worker.process("1");
        verify(channel, never()).submit(any());
        verify(delivery).waitOrEscalate(eq(expired), contains("时限"));
        var current = claim(Instant.now().plusSeconds(60));
        when(delivery.claim(1)).thenReturn(current);
        when(delivery.beforeSubmit(current)).thenReturn(false);
        worker.process("1");
        verify(channel, never()).submit(any());
    }

    private RefundDeliveryModels.Claim claim(Instant deadline) {
        return new RefundDeliveryModels.Claim(
                new RefundDeliveryModels.Task(
                        1,
                        1,
                        "READY",
                        "lease",
                        Instant.now().plusSeconds(30),
                        Instant.now(),
                        deadline,
                        0,
                        0,
                        null,
                        null),
                new RefundModels.Refund(
                        1, "id", 1, "order", BigDecimal.TEN, "PROCESSING", 1, null, Instant.now(), Instant.now()));
    }
}
