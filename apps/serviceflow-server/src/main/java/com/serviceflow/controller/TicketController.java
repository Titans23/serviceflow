package com.serviceflow.controller;

import com.serviceflow.model.TicketModels;
import com.serviceflow.security.CurrentPrincipal;
import com.serviceflow.service.TicketService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tickets")
public final class TicketController {
    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    @GetMapping
    List<TicketModels.TicketView> list(Authentication authentication) {
        return service.list(CurrentPrincipal.from(authentication).requireCustomerId());
    }
}
