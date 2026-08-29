package com.serviceflow.auth;

import com.serviceflow.config.ServiceFlowProperties;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public final class JwtService {
    private final JwtEncoder encoder;
    private final ServiceFlowProperties properties;

    public JwtService(JwtEncoder encoder, ServiceFlowProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public Token issueGuest() {
        return issue(
                "guest:" + UUID.randomUUID(),
                "GUEST",
                null,
                properties.jwt().guestTtl().toSeconds());
    }

    public Token issueUser(UserMapper.UserRow user) {
        return issue(
                Long.toString(user.id()),
                user.role(),
                user.customerId(),
                properties.jwt().customerTtl().toSeconds());
    }

    private Token issue(String subject, String role, Long customerId, long ttlSeconds) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("serviceflow")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .claim("role", role);
        if (customerId != null) {
            claims.claim("customerId", customerId);
        }
        String value = encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
        return new Token(value, ttlSeconds, role);
    }

    public record Token(String accessToken, long expiresIn, String role) {}
}
