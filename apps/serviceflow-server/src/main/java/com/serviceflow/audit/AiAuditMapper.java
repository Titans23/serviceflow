package com.serviceflow.audit;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiAuditMapper {

    int insert(
            @Param("publicId") String publicId,
            @Param("sessionId") long sessionId,
            @Param("clientRequestId") String clientRequestId,
            @Param("promptVersion") String promptVersion,
            @Param("modelName") String modelName,
            @Param("intent") String intent,
            @Param("productIds") String productIds,
            @Param("citations") String citations,
            @Param("degraded") boolean degraded,
            @Param("latencyMs") long latencyMs,
            @Param("resultStatus") String resultStatus,
            @Param("errorCode") String errorCode,
            @Param("question") String question,
            @Param("answer") String answer);

    List<AuditView> findRecent(@Param("limit") int limit);

    record AuditView(
            String publicId,
            String sessionPublicId,
            String customerName,
            String clientRequestId,
            String promptVersion,
            String modelName,
            String intent,
            String productIdsJson,
            String citationsJson,
            boolean degraded,
            long latencyMs,
            String resultStatus,
            String errorCode,
            String question,
            String answer,
            Instant createdAt) {}
}
