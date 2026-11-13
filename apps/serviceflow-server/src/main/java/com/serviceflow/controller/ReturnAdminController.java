package com.serviceflow.controller;

import com.serviceflow.model.ReturnModels;
import com.serviceflow.security.CurrentPrincipal;
import com.serviceflow.service.ReturnService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/returns")
public class ReturnAdminController {
    private final ReturnService returns;

    public ReturnAdminController(ReturnService returns) {
        this.returns = returns;
    }

    @GetMapping
    List<ReturnModels.View> list(@RequestParam(required = false) String status) {
        return returns.list(status);
    }

    @PostMapping("/{id}/review")
    ReturnModels.View review(@PathVariable String id, @Valid @RequestBody ReviewRequest request, Authentication auth) {
        return returns.review(
                id,
                request.decision(),
                request.note(),
                CurrentPrincipal.from(auth).subject());
    }

    @PostMapping("/{id}/receive")
    ReturnModels.View receive(
            @PathVariable String id, @Valid @RequestBody ReceiveRequest request, Authentication auth) {
        return returns.receive(id, request.note(), CurrentPrincipal.from(auth).subject());
    }

    public record ReviewRequest(
            @NotBlank @Pattern(regexp = "APPROVE|REJECT") String decision, @NotBlank @Size(max = 500) String note) {}

    public record ReceiveRequest(@NotBlank @Size(max = 500) String note) {}
}
