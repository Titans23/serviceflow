package com.serviceflow.auth;

import static org.springframework.http.HttpStatus.FORBIDDEN;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

public record CurrentPrincipal(String subject, String role, Long customerId) {
    public static CurrentPrincipal from(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Number customerId = jwt.getClaim("customerId");
        return new CurrentPrincipal(
                jwt.getSubject(), jwt.getClaimAsString("role"), customerId == null ? null : customerId.longValue());
    }

    public boolean guest() {
        return "GUEST".equals(role);
    }

    public long requireCustomerId() {
        if (customerId == null) throw new ResponseStatusException(FORBIDDEN, "该操作需要登录客户账号");
        return customerId;
    }
}
