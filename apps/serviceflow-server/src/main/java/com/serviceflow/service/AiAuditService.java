package com.serviceflow.service;

import com.serviceflow.agent.ServiceFlowState;
import com.serviceflow.model.AuditModels;
import java.util.List;

public interface AiAuditService {
    void success(
            Long sessionId,
            ServiceFlowState state,
            String question,
            String answer,
            List<String> citations,
            boolean degraded,
            long startedNanos);

    void failure(
            Long sessionId,
            String clientRequestId,
            ServiceFlowState state,
            String question,
            RuntimeException exception,
            long startedNanos);

    List<AuditModels.AuditView> recent(int limit);
}
