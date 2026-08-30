package com.serviceflow.mapper;

import com.serviceflow.model.TicketModels;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TicketMapper {
    int insert(
            @Param("publicId") String publicId,
            @Param("customerId") long customerId,
            @Param("sessionId") String sessionId,
            @Param("actionId") String actionId,
            @Param("subject") String subject,
            @Param("description") String description);

    List<TicketModels.TicketView> findByCustomer(@Param("customerId") long customerId);

    TicketModels.TicketView findByAction(@Param("actionId") String actionId, @Param("customerId") long customerId);

    List<TicketModels.AdminTicketView> findForAdmin(@Param("status") String status);

    TicketModels.AdminTicketView findAdminById(@Param("publicId") String publicId);

    int updateStatus(
            @Param("publicId") String publicId,
            @Param("currentStatus") String currentStatus,
            @Param("targetStatus") String targetStatus,
            @Param("operator") String operator,
            @Param("resolutionNote") String resolutionNote);
}
