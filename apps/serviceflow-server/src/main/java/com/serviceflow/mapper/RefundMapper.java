package com.serviceflow.mapper;

import com.serviceflow.model.RefundModels;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RefundMapper {
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
