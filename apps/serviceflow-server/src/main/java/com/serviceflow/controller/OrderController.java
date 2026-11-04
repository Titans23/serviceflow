package com.serviceflow.controller;

import com.serviceflow.model.OrderModels;
import com.serviceflow.security.CurrentPrincipal;
import com.serviceflow.service.OrderService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping
    OrderModels.Page list(
            Authentication authentication,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size) {
        return service.search(
                CurrentPrincipal.from(authentication).requireCustomerId(),
                new OrderModels.Search(keyword, status, from, to, orderNo),
                cursor,
                size);
    }
}
