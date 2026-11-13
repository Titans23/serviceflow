package com.serviceflow.controller;

import com.serviceflow.service.KnowledgeService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/knowledge-document-versions")
public final class KnowledgeVersionController {
    private final KnowledgeService service;

    public KnowledgeVersionController(KnowledgeService service) {
        this.service = service;
    }

    @PostMapping("/{versionId}/retry")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void retry(@PathVariable long versionId) {
        service.retry(versionId);
    }
}
