package com.serviceflow.service.impl;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.serviceflow.mapper.OrderMapper;
import com.serviceflow.mapper.RefundMapper;
import com.serviceflow.model.OrderModels;
import com.serviceflow.service.OrderService;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderServiceImpl implements OrderService {
    private static final Set<String> CANCELLABLE = Set.of("CREATED", "PAID", "PROCESSING");
    private final OrderMapper mapper;
    private final RefundMapper refunds;

    public OrderServiceImpl(OrderMapper mapper, RefundMapper refunds) {
        this.mapper = mapper;
        this.refunds = refunds;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderModels.OrderView get(String orderNo, long customerId) {
        OrderModels.Order order = owned(orderNo, customerId);
        var refund = refunds.findByOrder(order.id());
        return new OrderModels.OrderView(
                order.orderNo(),
                order.status(),
                order.totalAmount(),
                order.version(),
                order.createdAt(),
                mapper.items(order.id()),
                mapper.payment(order.id()),
                mapper.shipment(order.id()),
                refund == null ? null : refund.view());
    }

    // 取消订单、发起退款、记录操作结果必须作为一个整体提交；方法抛出运行时异常时一起回滚。
    // 未显式指定 isolation，因此使用数据库默认隔离级别（MySQL InnoDB 通常是 REPEATABLE READ）。
    @Transactional
    @Override
    public OrderModels.Operation cancel(String orderNo, long customerId, String requestId) {
        // An idempotency key is not authorization: bind the caller and target before returning any result.
        OrderModels.Order order = owned(orderNo, customerId);
        OrderModels.Operation existing = mapper.findOperation(requestId);
        if (existing != null) {
            if (!orderNo.equals(mapper.findOwnedOperationOrderNo(requestId, customerId))) {
                throw new ResponseStatusException(NOT_FOUND, "未找到可访问的已提交操作结果");
            }
            return existing;
        }

        // 真正的并发幂等控制：order_operation.request_id 有唯一约束。
        // 插入成功（1）表示当前请求取得执行权；返回 0 表示相同 requestId 已被另一个请求占用。
        if (mapper.reserveOperation(requestId, order.id()) == 0) {
            // 另一个事务已提交，但本事务的普通一致性读仍可能看不到它；使用当前读获取最终结果。
            OrderModels.Operation result = mapper.findLatestOperation(requestId, order.id());
            if (result == null) {
                throw new ResponseStatusException(NOT_FOUND, "未找到可访问的已提交操作结果");
            }
            return result;
        }

        // 幂等键只能阻止“同一个 requestId”重复执行；不同 requestId 仍要依靠订单状态阻止重复取消。
        if (!CANCELLABLE.contains(order.status())) {
            mapper.completeOperation(requestId, "NOT_CANCELLABLE", "订单已发货或结束，请按退货政策处理");
            return mapper.findOperation(requestId);
        }

        // 乐观锁：UPDATE 同时校验 customerId、旧 version 和可取消状态。
        // 影响行数不是 1，说明读取订单后它已被其他事务修改，本次不能继续发起退款。
        if (mapper.cancel(order.id(), customerId, order.version()) != 1) {
            mapper.completeOperation(requestId, "CONFLICT", "订单状态已变化，请刷新后重试");
            return mapper.findOperation(requestId);
        }

        // 只有订单状态更新成功后才进入退款流程，最后把幂等记录从 PROCESSING 更新为最终结果。
        boolean refundStarted = mapper.startRefund(order.id()) == 1;
        if (refundStarted) {
            if (mapper.createRefund(order.id(), requestId) != 1) {
                throw new IllegalStateException("Refund request could not be created; cancellation must roll back");
            }
        } else {
            var payment = mapper.payment(order.id());
            if (payment != null && "PAID".equals(payment.status())) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "支付退款状态异常，取消操作已回滚");
            }
        }
        mapper.completeOperation(requestId, "CANCELLED", refundStarted ? "订单已取消，已创建退款任务，请在订单详情查看处理进度。" : "订单已取消，无需退款。");
        return mapper.findOperation(requestId);
    }

    @Override
    public boolean canCancel(String orderNo, long customerId) {
        return CANCELLABLE.contains(owned(orderNo, customerId).status());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderModels.CancellationResult cancellationResult(String requestId, long customerId) {
        String orderNo = mapper.findOwnedOperationOrderNo(requestId, customerId);
        if (orderNo == null) {
            // The transaction may still be in flight; a missing result does not prove that no operation ran.
            throw new ResponseStatusException(NOT_FOUND, "未找到可访问的已提交操作结果");
        }
        return new OrderModels.CancellationResult(mapper.findOperation(requestId), get(orderNo, customerId));
    }

    private OrderModels.Order owned(String orderNo, long customerId) {
        OrderModels.Order order = mapper.findOwned(orderNo, customerId);
        if (order == null) {
            // 不区分“订单不存在”和“订单属于别人”，避免向当前用户泄露其他订单是否存在。
            throw new ResponseStatusException(NOT_FOUND, "订单不存在或无权访问");
        }
        return order;
    }
}
