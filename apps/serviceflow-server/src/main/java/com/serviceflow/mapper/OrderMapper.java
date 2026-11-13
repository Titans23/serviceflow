package com.serviceflow.mapper;

import com.serviceflow.model.OrderModels;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderMapper {
    com.serviceflow.model.ReturnModels.View returnRequest(@Param("orderId") long orderId);

    OrderModels.Order lockOwned(@Param("orderNo") String orderNo, @Param("customerId") long customerId);

    OrderModels.Order findOwned(@Param("orderNo") String orderNo, @Param("customerId") long customerId);

    OrderModels.Order findOwnedById(@Param("id") long id, @Param("customerId") long customerId);

    List<OrderModels.Order> searchOwned(
            @Param("customerId") long customerId,
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("status") String status,
            @Param("from") Instant from,
            @Param("until") Instant until,
            @Param("cursor") OrderModels.Order cursor,
            @Param("limit") int limit,
            @Param("orderNo") String orderNo);

    List<OrderModels.ItemRow> itemsForOrders(@Param("orderIds") List<Long> orderIds);

    List<OrderModels.Item> items(@Param("orderId") long orderId);

    OrderModels.Payment payment(@Param("orderId") long orderId);

    List<OrderModels.Shipment> shipment(@Param("orderId") long orderId);

    OrderModels.Operation findOperation(@Param("requestId") String requestId);

    OrderModels.Operation findLatestOperation(@Param("requestId") String requestId, @Param("orderId") long orderId);

    String findOwnedOperationOrderNo(@Param("requestId") String requestId, @Param("customerId") long customerId);

    int reserveOperation(@Param("requestId") String requestId, @Param("orderId") long orderId);

    int cancel(@Param("id") long id, @Param("customerId") long customerId, @Param("version") int version);

    int startRefund(@Param("orderId") long orderId);

    int createRefund(@Param("orderId") long orderId, @Param("publicId") String publicId);

    int completeOperation(
            @Param("requestId") String requestId, @Param("code") String code, @Param("message") String message);
}
