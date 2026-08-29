package com.serviceflow.chat;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.serviceflow.auth.CurrentPrincipal;
import com.serviceflow.order.OrderModels;
import com.serviceflow.order.OrderService;
import com.serviceflow.ticket.TicketMapper;
import com.serviceflow.ticket.TicketService;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public final class ChatActionService {
    private final ChatMemory memory;
    private final OrderService orders;
    private final TicketService tickets;
    private final SseEventWriter events;

    public ChatActionService(ChatMemory memory, OrderService orders, TicketService tickets, SseEventWriter events) {
        this.memory = memory;
        this.orders = orders;
        this.tickets = tickets;
        this.events = events;
    }

    public void confirm(CurrentPrincipal principal, String actionId, String decision, SseEmitter emitter) {
        if (principal.guest()) {
            throw new ResponseStatusException(FORBIDDEN, "访客不能执行该操作");
        }
        ChatMemory.PendingAction action = memory.action(actionId);
        if (action == null) {
            throw new ResponseStatusException(NOT_FOUND, "操作已过期");
        }
        if (!action.subject().equals(principal.subject()) || action.customerId() != principal.requireCustomerId()) {
            throw new ResponseStatusException(FORBIDDEN, "无权确认该操作");
        }

        if (!"CONFIRM".equalsIgnoreCase(decision)) {
            memory.removeAction(actionId);
            events.send(emitter, "token", "已取消本次操作。");
            events.send(emitter, "done", Map.of("actionId", actionId));
            emitter.complete();
            return;
        }

        if ("CANCEL_ORDER".equals(action.actionType())) {
            OrderModels.Operation result = orders.cancel(action.orderNo(), action.customerId(), actionId);
            events.send(emitter, "token", result.resultMessage());
            events.send(emitter, "done", Map.of("actionId", actionId, "resultCode", result.resultCode()));
        } else if ("CREATE_TICKET".equals(action.actionType())) {
            TicketMapper.TicketView ticket = tickets.createFromAction(
                    actionId, action.customerId(), action.sessionId(), "需要人工协助", action.query());
            events.send(emitter, "ticket", ticket);
            events.send(emitter, "token", "已创建人工工单 " + ticket.publicId() + "。");
            events.send(emitter, "done", Map.of("actionId", actionId));
        }
        emitter.complete();
    }
}
