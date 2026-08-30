package com.serviceflow.service;

import com.serviceflow.model.ChatModels;
import com.serviceflow.security.CurrentPrincipal;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {
    void stream(CurrentPrincipal principal, String sessionId, ChatModels.MessageRequest request, SseEmitter emitter);
}
