package com.serviceflow.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.agent.Intent;
import com.serviceflow.agent.ServiceFlowState;
import com.serviceflow.config.ServiceFlowProperties;
import com.serviceflow.mapper.AiAuditMapper;
import com.serviceflow.security.CurrentPrincipal;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class AuditPrivacyTest {
    @Test
    void contentRecordingRequiresExplicitConfigurationAndLatencyHasItsOwnMetric() {
        var mapper = mock(AiAuditMapper.class);
        var metrics = new SimpleMeterRegistry();
        var properties = new ServiceFlowProperties(
                null,
                new ServiceFlowProperties.Ai(
                        "cloud",
                        new ServiceFlowProperties.Ai.Chat("http://localhost", "disabled", "test-model"),
                        null,
                        null),
                null,
                null,
                null);
        var service = new AiAuditServiceImpl(mapper, new ObjectMapper(), metrics, properties, false);
        var state = new ServiceFlowState(
                "session", new CurrentPrincipal("owner", "CUSTOMER", 1L), "question", null, "request");
        state.intent(Intent.ORDER_QUERY);
        service.success(1L, state, "question", "answer", java.util.List.of(), false, System.nanoTime());
        verify(mapper)
                .insert(
                        anyString(),
                        eq(1L),
                        eq("request"),
                        anyString(),
                        eq("test-model"),
                        eq("ORDER_QUERY"),
                        anyString(),
                        anyString(),
                        eq(false),
                        anyLong(),
                        eq("SUCCESS"),
                        isNull(),
                        eq("[未记录正文]"),
                        isNull());
        service.firstToken(System.nanoTime() - 1000000);
        assertThat(metrics.get("serviceflow.chat.first_token_server").timer().count())
                .isEqualTo(1);
        new AiAuditServiceImpl(mapper, new ObjectMapper(), metrics, properties, true)
                .success(1L, state, "question", "answer", java.util.List.of(), false, System.nanoTime());
        verify(mapper)
                .insert(
                        anyString(),
                        eq(1L),
                        eq("request"),
                        anyString(),
                        eq("test-model"),
                        eq("ORDER_QUERY"),
                        anyString(),
                        anyString(),
                        eq(false),
                        anyLong(),
                        eq("SUCCESS"),
                        isNull(),
                        eq("question"),
                        eq("answer"));
    }
}
