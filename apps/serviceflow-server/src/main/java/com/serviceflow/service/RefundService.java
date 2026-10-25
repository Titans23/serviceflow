package com.serviceflow.service;

import com.serviceflow.model.RefundModels;
import java.util.List;

public interface RefundService {
    List<RefundModels.RefundView> recent(int limit);

    RefundModels.EventResult applySandboxEvent(String publicId, RefundModels.SandboxEvent event);

    RefundModels.RefundView retry(String publicId, int expectedAttemptNo);
}
