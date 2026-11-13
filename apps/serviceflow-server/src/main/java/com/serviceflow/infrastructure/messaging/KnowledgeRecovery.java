package com.serviceflow.infrastructure.messaging;

import com.serviceflow.mapper.KnowledgeMapper;
import com.serviceflow.service.KnowledgeService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeRecovery {
    private final KnowledgeMapper mapper;
    private final KnowledgeService knowledge;

    public KnowledgeRecovery(KnowledgeMapper mapper, KnowledgeService knowledge) {
        this.mapper = mapper;
        this.knowledge = knowledge;
    }

    @Scheduled(fixedDelayString = "${serviceflow.knowledge.recovery-interval:30s}")
    public void recoverExpired() {
        for (long id : mapper.expiredVersions()) knowledge.recover(id);
    }
}
