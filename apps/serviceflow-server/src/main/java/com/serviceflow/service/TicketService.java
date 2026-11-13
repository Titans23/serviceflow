package com.serviceflow.service;

import com.serviceflow.model.TicketModels;
import java.util.List;

public interface TicketService {
    TicketModels.Detail detail(String id, Long customerId);

    TicketModels.Message reply(String id, Long customerId, String messageId, String content);

    void closeByCustomer(String id, long customerId);

    void publishCustomerMessage(String publicId, String message);

    TicketModels.TicketView create(long customerId, String sessionId, String subject, String description);

    TicketModels.TicketView createFromAction(
            String actionId, long customerId, String sessionId, String subject, String description);

    List<TicketModels.TicketView> list(long customerId);

    List<TicketModels.AdminTicketView> listForAdmin(String status);

    TicketModels.AdminTicketView updateStatus(
            String publicId, String targetStatus, String resolutionNote, String operator);
}
