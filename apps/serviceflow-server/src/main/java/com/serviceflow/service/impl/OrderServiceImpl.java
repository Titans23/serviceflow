package com.serviceflow.service.impl;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.serviceflow.mapper.OrderMapper;
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

    public OrderServiceImpl(OrderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public OrderModels.OrderView get(String orderNo, long customerId) {
        OrderModels.Order order = owned(orderNo, customerId);
        return new OrderModels.OrderView(
                order.orderNo(),
                order.status(),
                order.totalAmount(),
                order.version(),
                order.createdAt(),
                mapper.items(order.id()),
                mapper.payment(order.id()),
                mapper.shipment(order.id()));
    }

    // 取消订单、发起退款、记录操作结果必须作为一个整体提交；方法抛出运行时异常时一起回滚。
    // 未显式指定 isolation，因此使用数据库默认隔离级别（MySQL InnoDB 通常是 REPEATABLE READ）。
    @Transactional
    @Override
    public OrderModels.Operation cancel(String orderNo, long customerId, String requestId) {
        // 快速幂等判断：同一个 requestId 已经处理过时，直接返回第一次保存的结果，避免重复查单和写库。
        OrderModels.Operation existing = mapper.findOperation(requestId);
        if (existing != null) {
            return existing;
        }

        // orderNo 和 customerId 必须同时匹配，既取得订单，也防止当前用户操作别人的订单。
        OrderModels.Order order = owned(orderNo, customerId);

        // 真正的并发幂等控制：order_operation.request_id 有唯一约束。
        // 插入成功（1）表示当前请求取得执行权；返回 0 表示相同 requestId 已被另一个请求占用。
        if (mapper.reserveOperation(requestId, order.id()) == 0) {
            // 另一个事务已提交，但本事务的普通一致性读仍可能看不到它；使用当前读获取最终结果。
            return mapper.findLatestOperation(requestId);
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
        mapper.startRefund(order.id());
        mapper.completeOperation(requestId, "CANCELLED", "订单已取消；已支付款项将进入退款流程");
        return mapper.findOperation(requestId);
    }

    @Override
    public boolean canCancel(String orderNo, long customerId) {
        return CANCELLABLE.contains(owned(orderNo, customerId).status());
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
