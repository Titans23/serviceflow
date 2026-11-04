package com.serviceflow.service.impl;

import com.serviceflow.model.ChatActionModels;
import com.serviceflow.security.CurrentPrincipal;
import com.serviceflow.service.ActionDecisionService;
import com.serviceflow.service.ChatActionService;
import com.serviceflow.web.sse.SseEventWriter;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public final class ChatActionServiceImpl implements ChatActionService {
    private final ActionDecisionService decisions;
    private final SseEventWriter events;

    public ChatActionServiceImpl(ActionDecisionService decisions, SseEventWriter events) {
        this.decisions = decisions;
        this.events = events;
    }

    @Override
    public ChatActionModels.Result result(CurrentPrincipal principal, String actionId) {
        return decisions.result(principal, actionId);
    }

    @Override
    public void confirm(CurrentPrincipal principal, String actionId, String decision, SseEmitter emitter) {
        var result = decisions.decide(principal, actionId, decision);
        // The proxied transaction has committed before the first transport write.
        events.send(emitter, "action_result", result);
        if (result.ticket() != null) events.send(emitter, "ticket", result.ticket());
        events.send(emitter, "token", result.operation().resultMessage());
        events.send(
                emitter,
                "done",
                Map.of(
                        "actionId",
                        actionId,
                        "actionStatus",
                        result.actionStatus(),
                        "resultCode",
                        result.operation().resultCode()));
        emitter.complete();
    }
}
