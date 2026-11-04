package com.serviceflow.controller;

import com.serviceflow.model.RefundModels;
import com.serviceflow.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/refunds")
@ConditionalOnExpression("${serviceflow.refund.sandbox-enabled:false} or ${serviceflow.refund.dispatch-enabled:false}")
public final class RefundRetryController {
    private final RefundService refunds;

    public RefundRetryController(RefundService refunds) {
        this.refunds = refunds;
    }

    @PostMapping("/{id}/retry")
    public RefundModels.RefundView retry(
            @PathVariable String id, @Valid @RequestBody RefundModels.RetryRequest request) {
        return refunds.retry(id, request.expectedAttemptNo());
    }
}
