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

    @Transactional
    @Override
    public OrderModels.Operation cancel(String orderNo, long customerId, String requestId) {
        OrderModels.Operation existing = mapper.findOperation(requestId);
        if (existing != null) {
            return existing;
        }
        OrderModels.Order order = owned(orderNo, customerId);
        if (mapper.reserveOperation(requestId, order.id()) == 0) {
            return mapper.findOperation(requestId);
        }
        if (!CANCELLABLE.contains(order.status())) {
            mapper.completeOperation(requestId, "NOT_CANCELLABLE", "订单已发货或结束，请按退货政策处理");
            return mapper.findOperation(requestId);
        }
        if (mapper.cancel(order.id(), customerId, order.version()) != 1) {
            mapper.completeOperation(requestId, "CONFLICT", "订单状态已变化，请刷新后重试");
            return mapper.findOperation(requestId);
        }
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
            throw new ResponseStatusException(NOT_FOUND, "订单不存在或无权访问");
        }
        return order;
    }
}
