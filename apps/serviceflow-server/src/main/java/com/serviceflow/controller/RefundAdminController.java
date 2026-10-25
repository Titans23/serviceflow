package com.serviceflow.controller;

import com.serviceflow.model.RefundModels;
import com.serviceflow.service.RefundService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/refunds")
public final class RefundAdminController {
    private final RefundService service;

    public RefundAdminController(RefundService service) {
        this.service = service;
    }

    @GetMapping
    List<RefundModels.RefundView> recent(@RequestParam(defaultValue = "100") int limit) {
        return service.recent(limit);
    }
}
