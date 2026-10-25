package com.serviceflow.controller;

import com.serviceflow.model.RefundModels;
import com.serviceflow.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Local simulation only; this is not a payment-provider webhook. ADMIN auth remains required. */
@RestController
@RequestMapping("/admin/refunds")
@ConditionalOnProperty(name = "serviceflow.refund.sandbox-enabled", havingValue = "true")
public final class RefundSandboxController {
    private final RefundService service;

    public RefundSandboxController(RefundService service) {
        this.service = service;
    }

    @PostMapping("/{publicId}/sandbox-events")
    RefundModels.EventResult event(@PathVariable String publicId, @Valid @RequestBody RefundModels.SandboxEvent input) {
        return service.applySandboxEvent(publicId, input);
    }

    @PostMapping("/{publicId}/retry")
    RefundModels.RefundView retry(@PathVariable String publicId, @Valid @RequestBody RefundModels.RetryRequest input) {
        return service.retry(publicId, input.expectedAttemptNo());
    }
}
