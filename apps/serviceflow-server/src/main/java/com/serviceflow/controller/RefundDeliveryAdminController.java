package com.serviceflow.controller;

import com.serviceflow.service.RefundDeliveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/refunds")
@ConditionalOnProperty(name = "serviceflow.refund.dispatch-enabled", havingValue = "true")
public final class RefundDeliveryAdminController {
    private final RefundDeliveryService delivery;

    public RefundDeliveryAdminController(RefundDeliveryService delivery) {
        this.delivery = delivery;
    }

    @PostMapping("/{id}/reconcile")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reconcile(@PathVariable String id) {
        delivery.reconcile(id);
    }
}
