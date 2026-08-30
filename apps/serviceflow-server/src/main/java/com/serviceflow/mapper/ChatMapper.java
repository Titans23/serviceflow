package com.serviceflow.mapper;

import com.serviceflow.model.ChatModels;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatMapper {
    int insertSession(
            @Param("publicId") String publicId, @Param("customerId") long customerId, @Param("title") String title);

    ChatModels.Session findSession(@Param("publicId") String publicId, @Param("customerId") long customerId);

    List<ChatModels.Session> listSessions(@Param("customerId") long customerId);

    Long sessionDatabaseId(@Param("publicId") String publicId, @Param("customerId") long customerId);

    List<ChatModels.Message> messages(@Param("sessionId") long sessionId);

    ChatModels.Message completed(@Param("sessionId") long sessionId, @Param("clientRequestId") String clientRequestId);

    int insertMessage(
            @Param("publicId") String publicId,
            @Param("sessionId") long sessionId,
            @Param("clientRequestId") String clientRequestId,
            @Param("role") String role,
            @Param("content") String content,
            @Param("intent") String intent,
            @Param("metadata") String metadata);

    int insertRequest(@Param("sessionId") long sessionId, @Param("clientRequestId") String clientRequestId);

    RequestRow request(@Param("sessionId") long sessionId, @Param("clientRequestId") String clientRequestId);

    int attachUserMessage(
            @Param("sessionId") long sessionId,
            @Param("clientRequestId") String clientRequestId,
            @Param("userMessageId") String userMessageId);

    int restartRequest(
            @Param("sessionId") long sessionId,
            @Param("clientRequestId") String clientRequestId,
            @Param("staleBefore") Instant staleBefore);

    int completeRequest(
            @Param("sessionId") long sessionId,
            @Param("clientRequestId") String clientRequestId,
            @Param("assistantMessageId") String assistantMessageId);

    int failRequest(
            @Param("sessionId") long sessionId,
            @Param("clientRequestId") String clientRequestId,
            @Param("errorCode") String errorCode);

    record RequestRow(String status, String userMessageId, String assistantMessageId, Instant updatedAt) {}
}
