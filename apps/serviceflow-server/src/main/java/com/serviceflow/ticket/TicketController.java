package com.serviceflow.ticket;

import com.serviceflow.auth.CurrentPrincipal;
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
    List<TicketMapper.TicketView> list(Authentication authentication) {
        return service.list(CurrentPrincipal.from(authentication).requireCustomerId());
    }
}
