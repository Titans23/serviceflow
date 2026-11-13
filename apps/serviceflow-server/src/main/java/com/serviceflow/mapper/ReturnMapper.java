package com.serviceflow.mapper;

import com.serviceflow.model.ReturnModels;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReturnMapper {
    ReturnModels.Reference find(@Param("publicId") String publicId);

    ReturnModels.Reference lockByOrder(@Param("orderId") long orderId);

    int insert(
            @Param("publicId") String publicId,
            @Param("orderId") long orderId,
            @Param("productName") String productName,
            @Param("amount") BigDecimal amount,
            @Param("reason") String reason);

    int review(
            @Param("publicId") String publicId,
            @Param("status") String status,
            @Param("note") String note,
            @Param("operator") String operator);

    int receive(@Param("publicId") String publicId, @Param("note") String note, @Param("operator") String operator);

    int createRefund(@Param("publicId") String publicId);

    List<ReturnModels.View> list(@Param("status") String status);
}
