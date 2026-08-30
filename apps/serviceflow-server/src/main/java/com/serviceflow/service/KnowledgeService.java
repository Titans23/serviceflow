package com.serviceflow.service;

import com.serviceflow.model.KnowledgeModels;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface KnowledgeService {
    KnowledgeModels.Version create(String title, String type, Long productId, MultipartFile file);

    KnowledgeModels.Version addVersion(String publicId, MultipartFile file);

    List<KnowledgeModels.DocumentView> list();

    void retry(long versionId);

    void activate(KnowledgeModels.Version version);
}
