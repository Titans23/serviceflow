package com.serviceflow.mapper;

import com.serviceflow.model.RefundModels;
import com.serviceflow.model.TicketModels;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RefundMapper {
    TicketModels.RefundReview review(
            @Param("refundId") long refundId, @Param("actionId") String actionId, @Param("customerId") long customerId);

    int enqueueDelivery(@Param("publicId") String publicId);

    RefundModels.Refund findById(@Param("id") long id);

    int notePending(@Param("id") long id, @Param("reason") String reason);

    RefundModels.Refund lock(@Param("publicId") String publicId);

    RefundModels.Refund find(@Param("publicId") String publicId);

    RefundModels.Refund findByOrder(@Param("orderId") long orderId);

    List<RefundModels.Refund> recent(@Param("limit") int limit);

    RefundModels.Event findEvent(@Param("eventId") String eventId);

    int insertEvent(RefundModels.Event event);

    int complete(
            @Param("id") long id,
            @Param("attemptNo") int attemptNo,
            @Param("outcome") String outcome,
            @Param("reason") String reason);

    int retry(@Param("id") long id, @Param("attemptNo") int attemptNo);

    int updatePayment(
            @Param("orderId") long orderId, @Param("expected") String expected, @Param("target") String target);
}
