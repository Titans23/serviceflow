package com.serviceflow.service.impl;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.serviceflow.mapper.TicketMapper;
import com.serviceflow.model.TicketModels;
import com.serviceflow.service.TicketService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TicketServiceImpl implements TicketService {
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "OPEN", Set.of("ASSIGNED"),
            "ASSIGNED", Set.of("RESOLVED"),
            "RESOLVED", Set.of("CLOSED"),
            "CLOSED", Set.of());
    private final TicketMapper mapper;

    public TicketServiceImpl(TicketMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public TicketModels.TicketView create(long customerId, String sessionId, String subject, String description) {
        String id = UUID.randomUUID().toString();
        mapper.insert(id, customerId, sessionId, null, subject, description);
        return mapper.findByCustomer(customerId).stream()
                .filter(ticket -> id.equals(ticket.publicId()))
                .findFirst()
                .orElseThrow();
    }

    @Override
    public TicketModels.TicketView createFromAction(
            String actionId, long customerId, String sessionId, String subject, String description) {
        TicketModels.TicketView existing = mapper.findByAction(actionId, customerId);
        if (existing != null) return existing;
        String id = UUID.randomUUID().toString();
        mapper.insert(id, customerId, sessionId, actionId, subject, description);
        return mapper.findByAction(actionId, customerId);
    }

    @Override
    public List<TicketModels.TicketView> list(long customerId) {
        return mapper.findByCustomer(customerId);
    }

    @Override
    public List<TicketModels.AdminTicketView> listForAdmin(String status) {
        if (status != null && !status.isBlank() && !TRANSITIONS.containsKey(status)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "无效工单状态");
        }
        return mapper.findForAdmin(status == null || status.isBlank() ? null : status);
    }

    @Override
    public TicketModels.AdminTicketView updateStatus(
            String publicId, String targetStatus, String resolutionNote, String operator) {
        TicketModels.AdminTicketView current = mapper.findAdminById(publicId);
        if (current == null) {
            throw new ResponseStatusException(NOT_FOUND, "工单不存在");
        }
        if (!TRANSITIONS.getOrDefault(current.status(), Set.of()).contains(targetStatus)) {
            throw new ResponseStatusException(CONFLICT, "工单状态只能从 " + current.status() + " 按流程向后流转");
        }
        String note = resolutionNote == null ? null : resolutionNote.trim();
        if (note != null && note.length() > 500) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "处理备注不能超过 500 字");
        }
        if (mapper.updateStatus(publicId, current.status(), targetStatus, operator, note) != 1) {
            throw new ResponseStatusException(CONFLICT, "工单状态已被其他客服更新");
        }
        return mapper.findAdminById(publicId);
    }
}
