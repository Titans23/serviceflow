package com.serviceflow.service;

import static org.springframework.http.HttpStatus.*;

import com.serviceflow.mapper.OrderMapper;
import com.serviceflow.mapper.RefundMapper;
import com.serviceflow.mapper.ReturnMapper;
import com.serviceflow.model.OrderModels;
import com.serviceflow.model.ReturnModels;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReturnService {
    private final ReturnMapper returns;
    private final OrderMapper orders;
    private final RefundMapper refunds;

    public ReturnService(ReturnMapper returns, OrderMapper orders, RefundMapper refunds) {
        this.returns = returns;
        this.orders = orders;
        this.refunds = refunds;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReturnModels.View apply(String orderNo, long customerId, String reason) {
        String note = note(reason);
        var order = orders.lockOwned(orderNo, customerId);
        if (order == null) throw new ResponseStatusException(NOT_FOUND, "订单不存在");
        // The order lock serializes concurrent applications, including requests with different request IDs.
        if (returns.lockByOrder(order.id()) != null) return orders.returnRequest(order.id());
        requireEligible(order);
        returns.insert(
                UUID.randomUUID().toString(),
                order.id(),
                orders.items(order.id()).getFirst().productName(),
                order.totalAmount(),
                note);
        return orders.returnRequest(order.id());
    }

    public List<ReturnModels.View> list(String status) {
        if (status != null
                && !Set.of("REQUESTED", "APPROVED", "REJECTED", "RECEIVED").contains(status))
            throw new ResponseStatusException(BAD_REQUEST, "无效退货状态");
        return returns.list(status);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReturnModels.View review(String id, String decision, String message, String operator) {
        String note = note(message);
        if (!Set.of("APPROVE", "REJECT").contains(decision)) throw new ResponseStatusException(BAD_REQUEST, "请选择通过或拒绝");
        var current = lock(id);
        String target = "APPROVE".equals(decision) ? "APPROVED" : "REJECTED";
        if (target.equals(current.status()) || ("APPROVE".equals(decision) && "RECEIVED".equals(current.status())))
            return orders.returnRequest(current.orderId());
        if (!"REQUESTED".equals(current.status())) throw new ResponseStatusException(CONFLICT, "退货申请已经审核");
        if ("APPROVE".equals(decision)) requireEligible(orders.findOwned(current.orderNo(), current.customerId()));
        if (returns.review(id, target, note, operator) != 1) throw new ResponseStatusException(CONFLICT, "退货状态已变化");
        return orders.returnRequest(current.orderId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReturnModels.View receive(String id, String message, String operator) {
        String note = note(message);
        var current = lock(id);
        if ("RECEIVED".equals(current.status())) return orders.returnRequest(current.orderId());
        if (!"APPROVED".equals(current.status())) throw new ResponseStatusException(CONFLICT, "审核通过后才能确认收到退货");
        requireEligible(orders.findOwned(current.orderNo(), current.customerId()));
        // Receipt, full refund and durable delivery commit together. The return ID is also the stable refund ID.
        if (returns.receive(id, note, operator) != 1
                || orders.startRefund(current.orderId()) != 1
                || returns.createRefund(id) != 1
                || refunds.enqueueDelivery(id) != 1) throw new ResponseStatusException(CONFLICT, "退货退款状态冲突，本次收货操作已回滚");
        return orders.returnRequest(current.orderId());
    }

    private ReturnModels.Reference lock(String id) {
        var reference = returns.find(id);
        if (reference == null) throw new ResponseStatusException(NOT_FOUND, "退货申请不存在");
        orders.lockOwned(reference.orderNo(), reference.customerId());
        return returns.lockByOrder(reference.orderId());
    }

    private void requireEligible(OrderModels.Order order) {
        var items = orders.items(order.id());
        var payment = orders.payment(order.id());
        if (!"DELIVERED".equals(order.status())
                || items.size() != 1
                || items.getFirst().quantity() != 1
                || order.totalAmount().signum() <= 0
                || payment == null
                || !"PAID".equals(payment.status())
                || !"NONE".equals(payment.refundStatus())
                || refunds.findByOrder(order.id()) != null)
            throw new ResponseStatusException(CONFLICT, "仅支持已签收、已付款且尚未退款的单件商品订单全额退货");
    }

    private String note(String value) {
        if (value == null || value.isBlank() || value.length() > 500)
            throw new ResponseStatusException(BAD_REQUEST, "请填写1到500字的说明");
        return value.trim();
    }
}
