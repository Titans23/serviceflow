package com.serviceflow.service;

import com.serviceflow.mapper.ChatActionMapper;
import com.serviceflow.mapper.OrderMapper;
import com.serviceflow.mapper.TicketMapper;
import com.serviceflow.model.ChatActionModels;
import com.serviceflow.model.OrderModels;
import com.serviceflow.security.CurrentPrincipal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** The transaction commits the user's decision and its business effects together, before any SSE write. */
@Service
public class ActionDecisionService {
    private final ChatActionMapper actions;
    private final OrderMapper orderMapper;
    private final OrderService orders;
    private final TicketService tickets;
    private final TicketMapper ticketMapper;

    public ActionDecisionService(
            ChatActionMapper actions,
            OrderMapper orderMapper,
            OrderService orders,
            TicketService tickets,
            TicketMapper ticketMapper) {
        this.actions = actions;
        this.orderMapper = orderMapper;
        this.orders = orders;
        this.tickets = tickets;
        this.ticketMapper = ticketMapper;
    }

    @Transactional
    public String prepare(ChatActionModels.Pending pending, String requestId) {
        if (!Set.of("CANCEL_ORDER", "CREATE_TICKET").contains(pending.actionType()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效操作类型");
        if (actions.lockSession(pending.sessionId(), pending.customerId()) == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        Integer version = "CANCEL_ORDER".equals(pending.actionType())
                ? orders.get(pending.orderNo(), pending.customerId()).version()
                : null;
        String source = requestId == null ? UUID.randomUUID().toString() : requestId;
        var action = new ChatActionModels.Action(
                UUID.randomUUID().toString(),
                pending.customerId(),
                pending.subject(),
                pending.sessionId(),
                source,
                pending.actionType(),
                pending.orderNo(),
                version,
                pending.query(),
                "PENDING",
                null,
                null,
                Instant.now().plus(10, ChronoUnit.MINUTES));
        actions.insert(action);
        var saved = actions.byRequest(pending.customerId(), pending.sessionId(), source, pending.actionType());
        if (!java.util.Objects.equals(saved.orderNo(), pending.orderNo()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同一请求不能更换操作订单");
        return saved.publicId();
    }

    @Transactional
    public ChatActionModels.Result decide(CurrentPrincipal principal, String id, String decision) {
        requireCustomer(principal);
        if (!Set.of("CONFIRM", "REJECT").contains(decision))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择确认或放弃");
        var action = actions.lock(id, principal.requireCustomerId());
        requireOwned(action, principal);
        if (!"PENDING".equals(action.status())) return view(action);
        if (!action.expiresAt().isAfter(Instant.now())) return finish(action, "EXPIRED", "EXPIRED", "操作已过期，请重新发起申请。");
        if (actions.lockSession(action.sessionPublicId(), action.customerId()) == null)
            return finish(action, "INVALIDATED", "SESSION_DELETED", "原会话已删除，本次操作未执行。");
        if ("REJECT".equals(decision)) return finish(action, "REJECTED", "REJECTED", "已放弃本次操作，订单未被本次申请修改。");
        if ("CANCEL_ORDER".equals(action.actionType())) {
            var current = orderMapper.lockOwned(action.orderNo(), action.customerId());
            if (current == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不可访问");
            if (current.version() != action.orderVersion())
                return finish(action, "CONFLICT", "CONFLICT", "订单状态已变化，本次申请未执行，请刷新后重新申请。");
            var operation = orders.cancel(action.orderNo(), action.customerId(), id);
            return finish(action, "CONFIRMED", operation.resultCode(), operation.resultMessage());
        }
        var ticket =
                tickets.createFromAction(id, action.customerId(), action.sessionPublicId(), "需要人工协助", action.query());
        return finish(action, "CONFIRMED", "TICKET_CREATED", "已创建人工工单 " + ticket.publicId() + "。");
    }

    @Transactional(readOnly = true)
    public ChatActionModels.Result result(CurrentPrincipal principal, String id) {
        requireCustomer(principal);
        var action = actions.find(id, principal.requireCustomerId());
        if (action == null) {
            // Released pre-migration cancellations still have durable order-operation receipts.
            var old = orders.cancellationResult(id, principal.requireCustomerId());
            return new ChatActionModels.Result("CONFIRMED", null, old.operation(), old.order(), null);
        }
        requireOwned(action, principal);
        return view(action);
    }

    private ChatActionModels.Result finish(ChatActionModels.Action action, String status, String code, String message) {
        if (actions.finish(action.publicId(), status, code, message) != 1)
            throw new IllegalStateException("Action decision lost its lock");
        return view(actions.lock(action.publicId(), action.customerId()));
    }

    private ChatActionModels.Result view(ChatActionModels.Action action) {
        String status = action.status(), code = action.resultCode(), message = action.resultMessage();
        if ("PENDING".equals(status)) {
            boolean expired = !action.expiresAt().isAfter(Instant.now());
            status = code = expired ? "EXPIRED" : "PENDING";
            message = expired ? "操作已过期，未执行。" : "尚未查到最终决定，请继续核验。";
        }
        return new ChatActionModels.Result(
                status,
                action.expiresAt(),
                new OrderModels.Operation(action.publicId(), code, message),
                action.orderNo() == null ? null : orders.get(action.orderNo(), action.customerId()),
                "TICKET_CREATED".equals(code)
                        ? ticketMapper.findByAction(action.publicId(), action.customerId())
                        : null);
    }

    private void requireOwned(ChatActionModels.Action action, CurrentPrincipal principal) {
        if (action == null || !action.subject().equals(principal.subject()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到可访问的操作");
    }

    private void requireCustomer(CurrentPrincipal principal) {
        if (!"CUSTOMER".equals(principal.role()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该操作需要登录客户账号");
    }
}
