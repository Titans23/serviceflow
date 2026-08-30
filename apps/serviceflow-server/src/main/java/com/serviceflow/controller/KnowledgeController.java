package com.serviceflow.controller;

import com.serviceflow.model.KnowledgeModels;
import com.serviceflow.service.KnowledgeService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/knowledge-documents")
public final class KnowledgeController {
    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    KnowledgeModels.Version create(
            @RequestParam String title,
            @RequestParam String documentType,
            @RequestParam(required = false) Long productId,
            @RequestPart MultipartFile file) {
        return service.create(title, documentType, productId, file);
    }

    @PostMapping(value = "/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    KnowledgeModels.Version version(@PathVariable String documentId, @RequestPart MultipartFile file) {
        return service.addVersion(documentId, file);
    }

    @GetMapping
    List<KnowledgeModels.DocumentView> list() {
        return service.list();
    }
}
