package com.serviceflow.mapper;

import com.serviceflow.model.ChatActionModels;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatActionMapper {
    int insert(ChatActionModels.Action action);

    ChatActionModels.Action find(@Param("id") String id, @Param("customerId") long customerId);

    ChatActionModels.Action lock(@Param("id") String id, @Param("customerId") long customerId);

    ChatActionModels.Action byRequest(
            @Param("customerId") long customerId,
            @Param("sessionId") String sessionId,
            @Param("requestId") String requestId,
            @Param("type") String type);

    Long lockSession(@Param("sessionId") String sessionId, @Param("customerId") long customerId);

    int finish(
            @Param("id") String id,
            @Param("status") String status,
            @Param("code") String code,
            @Param("message") String message);
}
