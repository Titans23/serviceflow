package com.serviceflow.service;

import com.serviceflow.model.ChatActionModels;
import com.serviceflow.security.CurrentPrincipal;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatActionService {
    void confirm(CurrentPrincipal principal, String actionId, String decision, SseEmitter emitter);

    ChatActionModels.Result result(CurrentPrincipal principal, String actionId);
}
