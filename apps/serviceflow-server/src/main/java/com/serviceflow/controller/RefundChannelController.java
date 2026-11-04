package com.serviceflow.controller;

import com.serviceflow.model.RefundModels;
import com.serviceflow.service.RefundDeliveryService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/refunds")
@ConditionalOnProperty(name = "serviceflow.refund.dispatch-enabled", havingValue = "true")
public final class RefundChannelController {
    private final RefundDeliveryService delivery;
    private final byte[] token;

    public RefundChannelController(
            RefundDeliveryService delivery, @Value("${serviceflow.refund.channel-token}") String token) {
        if (token.isBlank()) throw new IllegalArgumentException("Refund callback token is required");
        this.delivery = delivery;
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/{id}/events")
    public RefundModels.EventResult event(
            @PathVariable String id,
            @RequestHeader(value = "X-Refund-Token", defaultValue = "") String supplied,
            @Valid @RequestBody RefundModels.SandboxEvent event) {
        if (!MessageDigest.isEqual(token, supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无效渠道凭证");
        return delivery.callback(id, event);
    }
}
