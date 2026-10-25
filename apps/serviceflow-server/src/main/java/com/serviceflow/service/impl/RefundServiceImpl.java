package com.serviceflow.service.impl;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.serviceflow.mapper.RefundMapper;
import com.serviceflow.model.RefundModels;
import com.serviceflow.service.RefundService;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RefundServiceImpl implements RefundService {
    private final RefundMapper mapper;

    public RefundServiceImpl(RefundMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<RefundModels.RefundView> recent(int limit) {
        return mapper.recent(Math.max(1, Math.min(200, limit))).stream()
                .map(RefundModels.Refund::view)
                .toList();
    }

    @Override
    @Transactional
    public RefundModels.EventResult applySandboxEvent(String publicId, RefundModels.SandboxEvent input) {
        RefundModels.Refund refund = lock(publicId);
        String reason = input.reason() == null ? "" : input.reason().trim();
        if (!List.of("SUCCEEDED", "FAILED").contains(input.outcome())
                || input.attemptNo() < 1
                || input.amount() == null
                || input.amount().compareTo(refund.amount()) != 0
                || reason.length() > 500
                || ("FAILED".equals(input.outcome()) && reason.isEmpty())) {
            throw new ResponseStatusException(BAD_REQUEST, "退款金额、结果或失败原因无效");
        }
        RefundModels.Event previous = mapper.findEvent(input.eventId());
        if (previous != null) {
            if (previous.refundId() != refund.id()
                    || previous.attemptNo() != input.attemptNo()
                    || !previous.outcome().equals(input.outcome())
                    || previous.amount().compareTo(input.amount()) != 0
                    || !previous.reason().equals(reason)) {
                throw new ResponseStatusException(CONFLICT, "事件编号已被不同内容使用");
            }
            return new RefundModels.EventResult(refund.view(), true, previous.disposition());
        }
        if (input.attemptNo() > refund.attemptNo()) {
            throw new ResponseStatusException(CONFLICT, "回调重试次数超过当前退款任务");
        }
        String disposition;
        if (input.attemptNo() < refund.attemptNo()) {
            disposition = "STALE_ATTEMPT";
        } else if ("PROCESSING".equals(refund.status())) {
            disposition = "APPLIED";
        } else if (refund.status().equals(input.outcome())) {
            disposition = "DUPLICATE_OUTCOME";
        } else {
            throw new ResponseStatusException(CONFLICT, "本次退款已结束，不接受冲突回调");
        }
        try {
            mapper.insertEvent(new RefundModels.Event(
                    input.eventId(),
                    refund.id(),
                    input.attemptNo(),
                    input.outcome(),
                    input.amount(),
                    reason,
                    disposition));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(CONFLICT, "事件编号已被并发请求使用");
        }
        if ("APPLIED".equals(disposition)) {
            if (mapper.complete(
                                    refund.id(),
                                    refund.attemptNo(),
                                    input.outcome(),
                                    "FAILED".equals(input.outcome()) ? reason : null)
                            != 1
                    || mapper.updatePayment(
                                    refund.orderId(),
                                    "PROCESSING",
                                    "SUCCEEDED".equals(input.outcome()) ? "REFUNDED" : "FAILED")
                            != 1) {
                throw new ResponseStatusException(CONFLICT, "退款状态不一致，事务已回滚");
            }
        }
        return new RefundModels.EventResult(mapper.find(publicId).view(), false, disposition);
    }

    @Override
    @Transactional
    public RefundModels.RefundView retry(String publicId, int expectedAttemptNo) {
        RefundModels.Refund refund = lock(publicId);
        if (expectedAttemptNo < 1) throw new ResponseStatusException(BAD_REQUEST, "无效重试次数");
        if ("PROCESSING".equals(refund.status()) && refund.attemptNo() == expectedAttemptNo + 1) {
            return refund.view();
        }
        if (!"FAILED".equals(refund.status()) || refund.attemptNo() != expectedAttemptNo) {
            throw new ResponseStatusException(CONFLICT, "只允许重试当前失败的退款任务");
        }
        if (mapper.retry(refund.id(), expectedAttemptNo) != 1
                || mapper.updatePayment(refund.orderId(), "FAILED", "PROCESSING") != 1) {
            throw new ResponseStatusException(CONFLICT, "退款状态已变化，事务已回滚");
        }
        return mapper.find(publicId).view();
    }

    private RefundModels.Refund lock(String publicId) {
        RefundModels.Refund refund = mapper.lock(publicId);
        if (refund == null) throw new ResponseStatusException(NOT_FOUND, "退款任务不存在");
        return refund;
    }
}
