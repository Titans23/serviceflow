package com.serviceflow.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.serviceflow.mapper.RefundMapper;
import com.serviceflow.model.RefundModels;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.server.ResponseStatusException;

class RefundServiceTest {
    private RefundMapper mapper;
    private RefundServiceImpl service;
    private static final String ID = "refund-1";
    private static final String EVENT = "81e650ec-5b42-4c9d-9a0c-d2be46372576";

    @BeforeEach
    void setUp() {
        mapper = mock(RefundMapper.class);
        service = new RefundServiceImpl(mapper);
        when(mapper.lock(ID)).thenReturn(refund("PROCESSING", 1));
        when(mapper.find(ID)).thenReturn(refund("SUCCEEDED", 1));
        when(mapper.complete(anyLong(), anyInt(), anyString(), any())).thenReturn(1);
        when(mapper.updatePayment(anyLong(), anyString(), anyString())).thenReturn(1);
    }

    @Test
    void successAppliesToBothRefundAndPayment() {
        var result = service.applySandboxEvent(ID, event(1, "SUCCEEDED", ""));
        assertThat(result.disposition()).isEqualTo("APPLIED");
        assertThat(result.refund().status()).isEqualTo("SUCCEEDED");
        verify(mapper).complete(1, 1, "SUCCEEDED", null);
        verify(mapper).updatePayment(9, "PROCESSING", "REFUNDED");
    }

    @Test
    void failurePreservesReasonAndPaymentFailure() {
        when(mapper.find(ID)).thenReturn(refund("FAILED", 1));
        service.applySandboxEvent(ID, event(1, "FAILED", "通道暂不可用"));
        verify(mapper).complete(1, 1, "FAILED", "通道暂不可用");
        verify(mapper).updatePayment(9, "PROCESSING", "FAILED");
    }

    @Test
    void sameEventIsReplayedWithoutSideEffects() {
        when(mapper.findEvent(EVENT))
                .thenReturn(new RefundModels.Event(EVENT, 1, 1, "SUCCEEDED", BigDecimal.TEN, "", "APPLIED"));
        assertThat(service.applySandboxEvent(ID, event(1, "SUCCEEDED", "")).replayed())
                .isTrue();
        verify(mapper, never()).insertEvent(any());
        verify(mapper, never()).updatePayment(anyLong(), anyString(), anyString());
    }

    @Test
    void eventIdCannotBeReusedForDifferentPayloadOrRefund() {
        when(mapper.findEvent(EVENT))
                .thenReturn(new RefundModels.Event(EVENT, 77, 1, "SUCCEEDED", BigDecimal.TEN, "", "APPLIED"));
        assertThatThrownBy(() -> service.applySandboxEvent(ID, event(1, "SUCCEEDED", "")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不同内容");
    }

    @Test
    void lateOldAttemptIsRecordedButCannotChangeCurrentAttempt() {
        when(mapper.lock(ID)).thenReturn(refund("PROCESSING", 2));
        when(mapper.find(ID)).thenReturn(refund("PROCESSING", 2));
        assertThat(service.applySandboxEvent(ID, event(1, "SUCCEEDED", "")).disposition())
                .isEqualTo("STALE_ATTEMPT");
        verify(mapper, never()).complete(anyLong(), anyInt(), anyString(), any());
        verify(mapper, never()).updatePayment(anyLong(), anyString(), anyString());
    }

    @Test
    void rejectsFutureAttemptAndWrongAmount() {
        assertThatThrownBy(() -> service.applySandboxEvent(ID, event(2, "SUCCEEDED", "")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("超过");
        assertThatThrownBy(() -> service.applySandboxEvent(
                        ID, new RefundModels.SandboxEvent(EVENT, 1, "SUCCEEDED", BigDecimal.ONE, "")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("金额");
        verify(mapper, never()).insertEvent(any());
    }

    @Test
    void terminalSuccessCannotBeReversedByFailure() {
        when(mapper.lock(ID)).thenReturn(refund("SUCCEEDED", 1));
        assertThatThrownBy(() -> service.applySandboxEvent(ID, event(1, "FAILED", "迟到失败")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("冲突回调");
        verify(mapper, never()).insertEvent(any());
    }

    @Test
    void sameOutcomeWithDifferentEventDoesNotRepeatPaymentChange() {
        when(mapper.lock(ID)).thenReturn(refund("SUCCEEDED", 1));
        assertThat(service.applySandboxEvent(ID, event(1, "SUCCEEDED", "")).disposition())
                .isEqualTo("DUPLICATE_OUTCOME");
        verify(mapper, never()).updatePayment(anyLong(), anyString(), anyString());
    }

    @Test
    void failedAttemptCanBeRetriedOnce() {
        when(mapper.lock(ID)).thenReturn(refund("FAILED", 1));
        when(mapper.retry(1, 1)).thenReturn(1);
        when(mapper.find(ID)).thenReturn(refund("PROCESSING", 2));
        assertThat(service.retry(ID, 1).attemptNo()).isEqualTo(2);
        verify(mapper).updatePayment(9, "FAILED", "PROCESSING");
    }

    @Test
    void repeatedRetryDoesNotIncrementAgain() {
        when(mapper.lock(ID)).thenReturn(refund("PROCESSING", 2));
        assertThat(service.retry(ID, 1).attemptNo()).isEqualTo(2);
        verify(mapper, never()).retry(anyLong(), anyInt());
    }

    @Test
    void rejectsRetryOfSuccessfulOrDifferentAttempt() {
        when(mapper.lock(ID)).thenReturn(refund("SUCCEEDED", 1));
        assertThatThrownBy(() -> service.retry(ID, 1)).isInstanceOf(ResponseStatusException.class);
        when(mapper.lock(ID)).thenReturn(refund("FAILED", 2));
        assertThatThrownBy(() -> service.retry(ID, 1)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void inconsistentPaymentRaisesTransactionalFailure() {
        when(mapper.updatePayment(9, "PROCESSING", "REFUNDED")).thenReturn(0);
        assertThatThrownBy(() -> service.applySandboxEvent(ID, event(1, "SUCCEEDED", "")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("回滚");
    }

    @Test
    void invalidOutcomeAndMissingFailureReasonAreRejected() {
        assertThatThrownBy(() -> service.applySandboxEvent(ID, event(1, "REFUNDED", "")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.applySandboxEvent(ID, event(1, "FAILED", "  ")))
                .isInstanceOf(ResponseStatusException.class);
        verify(mapper, never()).insertEvent(any());
    }

    @Test
    void eventCollisionDoesNotUpdateRefund() {
        doThrow(new DuplicateKeyException("event collision")).when(mapper).insertEvent(any());
        assertThatThrownBy(() -> service.applySandboxEvent(ID, event(1, "SUCCEEDED", "")))
                .isInstanceOf(ResponseStatusException.class);
        verify(mapper, never()).complete(anyLong(), anyInt(), anyString(), any());
    }

    @Test
    void unknownRefundCannotBeModified() {
        when(mapper.lock(ID)).thenReturn(null);
        assertThatThrownBy(() -> service.retry(ID, 1))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不存在");
    }

    private RefundModels.SandboxEvent event(int attempt, String outcome, String reason) {
        return new RefundModels.SandboxEvent(EVENT, attempt, outcome, BigDecimal.TEN, reason);
    }

    private RefundModels.Refund refund(String status, int attempt) {
        return new RefundModels.Refund(
                1, ID, 9, "SF123456789012", BigDecimal.TEN, status, attempt, null, Instant.EPOCH, Instant.EPOCH);
    }
}
