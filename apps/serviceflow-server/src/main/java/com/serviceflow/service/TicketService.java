package com.serviceflow.service;

import com.serviceflow.model.TicketModels;
import java.util.List;

public interface TicketService {
    TicketModels.TicketView create(long customerId, String sessionId, String subject, String description);

    TicketModels.TicketView createFromAction(
            String actionId, long customerId, String sessionId, String subject, String description);

    List<TicketModels.TicketView> list(long customerId);

    List<TicketModels.AdminTicketView> listForAdmin(String status);

    TicketModels.AdminTicketView updateStatus(
            String publicId, String targetStatus, String resolutionNote, String operator);
}
