package com.serviceflow.ticket;

import java.time.Instant;
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

    List<TicketView> findByCustomer(@Param("customerId") long customerId);

    TicketView findByAction(@Param("actionId") String actionId, @Param("customerId") long customerId);

    List<AdminTicketView> findForAdmin(@Param("status") String status);

    AdminTicketView findAdminById(@Param("publicId") String publicId);

    int updateStatus(
            @Param("publicId") String publicId,
            @Param("currentStatus") String currentStatus,
            @Param("targetStatus") String targetStatus,
            @Param("operator") String operator,
            @Param("resolutionNote") String resolutionNote);

    record TicketView(String publicId, String subject, String description, String status, Instant createdAt) {}

    record AdminTicketView(
            String publicId,
            long customerId,
            String customerName,
            String sessionPublicId,
            String subject,
            String description,
            String status,
            String assignedTo,
            String resolutionNote,
            Instant createdAt,
            Instant updatedAt) {}
}
