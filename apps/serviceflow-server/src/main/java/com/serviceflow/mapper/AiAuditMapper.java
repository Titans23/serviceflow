package com.serviceflow.mapper;

import com.serviceflow.model.AuditModels;
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

    List<AuditModels.AuditView> findRecent(@Param("limit") int limit);
}
