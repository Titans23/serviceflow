package com.serviceflow.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.serviceflow.config.SecurityConfig;
import com.serviceflow.config.ServiceFlowProperties;
import com.serviceflow.service.RefundDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = RefundChannelController.class,
        properties = {
            "serviceflow.refund.dispatch-enabled=true",
            "serviceflow.refund.channel-token=fixture-channel-token"
        })
@Import(SecurityConfig.class)
@EnableConfigurationProperties(ServiceFlowProperties.class)
class RefundChannelAccessTest {
    private final MockMvc http;

    @MockitoBean
    private RefundDeliveryService delivery;

    @Autowired
    RefundChannelAccessTest(MockMvc http) {
        this.http = http;
    }

    @Test
    void webhookRequiresChannelCredentialAndValidEvent() throws Exception {
        String payload =
                """
                {"eventId":"81e650ec-5b42-4c9d-9a0c-d2be46372576","attemptNo":1,"outcome":"SUCCEEDED","amount":10}
                """;
        http.perform(post("/internal/refunds/one/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
        verifyNoInteractions(delivery);
        http.perform(post("/internal/refunds/one/events")
                        .header("X-Refund-Token", "fixture-channel-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.replace("SUCCEEDED", "UNKNOWN")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(delivery);
        http.perform(post("/internal/refunds/one/events")
                        .header("X-Refund-Token", "fixture-channel-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
        verify(delivery).callback(eq("one"), any());
    }
}
