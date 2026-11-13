package com.serviceflow.controller;

import com.serviceflow.model.TicketModels;
import com.serviceflow.security.CurrentPrincipal;
import com.serviceflow.service.TicketService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @GetMapping("/{id}")
    TicketModels.Detail detail(@PathVariable String id, Authentication authentication) {
        return service.detail(id, CurrentPrincipal.from(authentication).requireCustomerId());
    }

    @PostMapping("/{id}/messages")
    TicketModels.Message reply(
            @PathVariable String id, @Valid @RequestBody MessageRequest request, Authentication authentication) {
        return service.reply(
                id,
                CurrentPrincipal.from(authentication).requireCustomerId(),
                request.clientRequestId(),
                request.content());
    }

    @PostMapping("/{id}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void close(@PathVariable String id, Authentication authentication) {
        service.closeByCustomer(id, CurrentPrincipal.from(authentication).requireCustomerId());
    }

    public record MessageRequest(
            @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                    String clientRequestId,
            @NotBlank @Size(max = 500) String content) {}
}
