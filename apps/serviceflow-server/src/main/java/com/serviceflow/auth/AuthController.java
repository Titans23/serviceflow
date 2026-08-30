package com.serviceflow.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class AuthController {
    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/auth/guest")
    JwtService.Token guest() {
        return jwtService.issueGuest();
    }

    @PostMapping("/auth/login")
    JwtService.Token login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    @GetMapping("/me")
    CurrentPrincipal me(Authentication authentication) {
        return CurrentPrincipal.from(authentication);
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
}
