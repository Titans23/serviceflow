package com.serviceflow.service;

import com.serviceflow.mapper.RefundDeliveryMapper;
import com.serviceflow.mapper.RefundMapper;
import com.serviceflow.model.RefundDeliveryModels;
import com.serviceflow.model.RefundModels;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundDeliveryService {
    private final RefundDeliveryMapper tasks;
    private final RefundMapper refunds;
    private final RefundService refundService;
    private final TicketService tickets;

    public RefundDeliveryService(
            RefundDeliveryMapper tasks, RefundMapper refunds, RefundService refundService, TicketService tickets) {
        this.tasks = tasks;
        this.refunds = refunds;
        this.refundService = refundService;
        this.tickets = tickets;
    }

    @Transactional
    public RefundDeliveryModels.Claim claim(long id) {
        String token = UUID.randomUUID().toString();
        if (tasks.claim(id, token) != 1) return null;
        var task = tasks.lock(id);
        var refund = refunds.findById(id);
        if (!"PROCESSING".equals(refund.status())) {
            tasks.release(id, token, "DONE", null, null);
            return null;
        }
        return new RefundDeliveryModels.Claim(task, refund);
    }

    public void queried(RefundDeliveryModels.Claim claim) {
        tasks.queried(claim.task().refundId(), claim.task().leaseToken());
    }

    public boolean beforeSubmit(RefundDeliveryModels.Claim claim) {
        return tasks.submitted(claim.task().refundId(), claim.task().leaseToken()) == 1;
    }

    @Transactional
    public void accept(RefundDeliveryModels.Claim claim, RefundDeliveryModels.ChannelResult result) {
        // Match retry's lock order: refund/order first, delivery second.
        refunds.lock(claim.refund().publicId());
        var current = tasks.lock(claim.task().refundId());
        if (!claim.task().leaseToken().equals(current.leaseToken())) return;
        if (!claim.refund().publicId().equals(result.refundId())
                || claim.task().attemptNo() != result.attemptNo()
                || result.amount() == null
                || result.amount().compareTo(claim.refund().amount()) != 0)
            throw new IllegalStateException("Channel receipt does not match refund");
        callback(
                result.refundId(),
                new RefundModels.SandboxEvent(
                        eventId(result), result.attemptNo(), result.status(), result.amount(), result.reason()));
    }

    @Transactional
    public RefundModels.EventResult callback(String id, RefundModels.SandboxEvent event) {
        var result = refundService.applySandboxEvent(id, event);
        if (!"STALE_ATTEMPT".equals(result.disposition())) {
            var refund = refunds.find(id);
            tasks.terminal(refund.id(), event.attemptNo());
        }
        return result;
    }

    @Transactional
    public void waitOrEscalate(RefundDeliveryModels.Claim claim, String reason) {
        var refund = refunds.lock(claim.refund().publicId());
        var current = tasks.lock(claim.task().refundId());
        if (!claim.task().leaseToken().equals(current.leaseToken())) return;
        if (!"PROCESSING".equals(refund.status())) {
            tasks.release(refund.id(), current.leaseToken(), "DONE", null, null);
            return;
        }
        if (current.deadlineAt().isAfter(Instant.now())) {
            tasks.release(refund.id(), current.leaseToken(), "WAITING", reason, null);
            return;
        }
        var owner = tasks.owner(refund.id());
        String key = RefundDeliveryModels.reviewActionId(refund.publicId());
        var ticket = tickets.createFromAction(
                key,
                owner.customerId(),
                owner.sessionId(),
                "退款渠道结果待人工核验",
                "订单 " + refund.orderNo() + "；退款 " + refund.publicId() + "；金额 " + refund.amount()
                        + "；批次 " + refund.attemptNo() + "；查询次数 " + current.queryCount()
                        + "；提交次数 " + current.submitCount() + "；最近情况：" + reason
                        + "。请先核对渠道记录，禁止在结果未知时重新发起退款。");
        refunds.notePending(refund.id(), "渠道结果仍待核实，已转人工工单 " + ticket.publicId());
        tasks.release(refund.id(), current.leaseToken(), "MANUAL", reason, ticket.publicId());
    }

    private String eventId(RefundDeliveryModels.ChannelResult result) {
        return UUID.nameUUIDFromBytes(
                        ("channel:" + result.refundId() + ":" + result.attemptNo() + ":" + result.status())
                                .getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    @Transactional
    public void reconcile(String id) {
        var refund = refunds.lock(id);
        if (refund == null)
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "退款不存在");
        var task = tasks.lock(refund.id());
        if (task == null)
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "此退款没有后台渠道任务");
        if ("PROCESSING".equals(refund.status()) && tasks.reopen(refund.id()) == 1)
            refunds.notePending(refund.id(), "已请求再次核验渠道，原人工工单 " + task.ticketId());
    }
}
