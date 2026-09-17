package com.serviceflow.mapper;

import com.serviceflow.model.OrderModels;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderMapper {
    OrderModels.Order findOwned(@Param("orderNo") String orderNo, @Param("customerId") long customerId);

    List<OrderModels.Item> items(@Param("orderId") long orderId);

    OrderModels.Payment payment(@Param("orderId") long orderId);

    List<OrderModels.Shipment> shipment(@Param("orderId") long orderId);

    OrderModels.Operation findOperation(@Param("requestId") String requestId);

    OrderModels.Operation findLatestOperation(@Param("requestId") String requestId);

    int reserveOperation(@Param("requestId") String requestId, @Param("orderId") long orderId);

    int cancel(@Param("id") long id, @Param("customerId") long customerId, @Param("version") int version);

    int startRefund(@Param("orderId") long orderId);

    int completeOperation(
            @Param("requestId") String requestId, @Param("code") String code, @Param("message") String message);
}
