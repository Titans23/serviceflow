package com.serviceflow.controller;

import com.serviceflow.model.OrderModels;
import com.serviceflow.security.CurrentPrincipal;
import com.serviceflow.service.OrderService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public final class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping("/{orderNo}")
    OrderModels.OrderView get(@PathVariable String orderNo, Authentication authentication) {
        return service.get(orderNo, CurrentPrincipal.from(authentication).requireCustomerId());
    }
}
