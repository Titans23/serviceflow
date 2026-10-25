package com.serviceflow.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.serviceflow.config.SecurityConfig;
import com.serviceflow.config.ServiceFlowProperties;
import com.serviceflow.service.RefundService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RefundSandboxController.class, properties = "serviceflow.refund.sandbox-enabled=true")
@Import(SecurityConfig.class)
@EnableConfigurationProperties(ServiceFlowProperties.class)
class RefundSandboxAccessTest {
    private final MockMvc http;

    @Autowired
    RefundSandboxAccessTest(MockMvc http) {
        this.http = http;
    }

    @MockitoBean
    private RefundService refunds;

    private static final String BODY =
            """
            {"eventId":"81e650ec-5b42-4c9d-9a0c-d2be46372576","attemptNo":1,"outcome":"SUCCEEDED","amount":10}
            """;

    @Test
    void unauthenticatedAndCustomerCannotSendCallbacks() throws Exception {
        http.perform(post("/admin/refunds/one/sandbox-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        http.perform(post("/admin/refunds/one/sandbox-events")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(refunds);
    }

    @Test
    void administratorCanSendValidatedCallback() throws Exception {
        http.perform(post("/admin/refunds/one/sandbox-events")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());
        verify(refunds).applySandboxEvent(eq("one"), any());
    }

    @Test
    void malformedEventIsRejectedBeforeBusinessCode() throws Exception {
        http.perform(post("/admin/refunds/one/sandbox-events")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("SUCCEEDED", "UNKNOWN")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(refunds);
    }
}
