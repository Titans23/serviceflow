package com.serviceflow.audit;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/ai-audits")
public final class AiAuditController {

    private final AiAuditService service;

    public AiAuditController(AiAuditService service) {
        this.service = service;
    }

    @GetMapping
    List<AiAuditMapper.AuditView> recent(@RequestParam(defaultValue = "100") int limit) {
        return service.recent(limit);
    }
}
