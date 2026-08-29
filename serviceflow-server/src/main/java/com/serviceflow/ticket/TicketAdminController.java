package com.serviceflow.ticket;

import com.serviceflow.auth.CurrentPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/tickets")
public final class TicketAdminController {

    private final TicketService service;

    public TicketAdminController(TicketService service) {
        this.service = service;
    }

    @GetMapping
    List<TicketMapper.AdminTicketView> list(@RequestParam(required = false) String status) {
        return service.listForAdmin(status);
    }

    @PutMapping("/{publicId}/status")
    TicketMapper.AdminTicketView update(
            @PathVariable String publicId,
            @Valid @RequestBody UpdateStatusRequest request,
            Authentication authentication) {
        CurrentPrincipal principal = CurrentPrincipal.from(authentication);
        return service.updateStatus(publicId, request.status(), request.resolutionNote(), principal.subject());
    }

    public record UpdateStatusRequest(@NotBlank String status, String resolutionNote) {}
}
