package com.serviceflow.controller;

import com.serviceflow.model.ReturnModels;
import com.serviceflow.security.CurrentPrincipal;
import com.serviceflow.service.ReturnService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders/{orderNo}/return")
public class ReturnController {
    private final ReturnService returns;

    public ReturnController(ReturnService returns) {
        this.returns = returns;
    }

    @PostMapping
    ReturnModels.View apply(
            @PathVariable String orderNo, @Valid @RequestBody ApplyRequest request, Authentication auth) {
        return returns.apply(orderNo, CurrentPrincipal.from(auth).requireCustomerId(), request.reason());
    }

    public record ApplyRequest(@NotBlank @Size(max = 500) String reason) {}
}
