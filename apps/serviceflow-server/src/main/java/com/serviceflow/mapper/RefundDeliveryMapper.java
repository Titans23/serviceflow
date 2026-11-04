package com.serviceflow.mapper;

import com.serviceflow.model.RefundDeliveryModels;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RefundDeliveryMapper {
    List<Long> due();

    int published(@Param("id") long id);

    RefundDeliveryModels.Task lock(@Param("id") long id);

    int claim(@Param("id") long id, @Param("token") String token);

    int release(
            @Param("id") long id,
            @Param("token") String token,
            @Param("state") String state,
            @Param("error") String error,
            @Param("ticketId") String ticketId);

    int queried(@Param("id") long id, @Param("token") String token);

    int submitted(@Param("id") long id, @Param("token") String token);

    int terminal(@Param("id") long id, @Param("attempt") int attempt);

    int reopen(@Param("id") long id);

    RefundDeliveryModels.Owner owner(@Param("id") long id);
}
