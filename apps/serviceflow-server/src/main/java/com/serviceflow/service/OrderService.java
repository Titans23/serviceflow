package com.serviceflow.service;

import com.serviceflow.model.OrderModels;

public interface OrderService {
    OrderModels.OrderView get(String orderNo, long customerId);

    OrderModels.Operation cancel(String orderNo, long customerId, String requestId);

    OrderModels.CancellationResult cancellationResult(String requestId, long customerId);

    boolean canCancel(String orderNo, long customerId);
}
