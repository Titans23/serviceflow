package com.serviceflow.model;

public final class AuthModels {
    private AuthModels() {}

    public record User(long id, String username, String passwordHash, String role, Long customerId, boolean enabled) {}

    public record Token(String accessToken, long expiresIn, String role) {}
}
