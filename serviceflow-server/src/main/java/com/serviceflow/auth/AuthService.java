package com.serviceflow.auth;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UserMapper users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserMapper users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public JwtService.Token login(String username, String password) {
        UserMapper.UserRow user = users.findByUsername(username);
        if (user == null || !user.enabled() || !passwordEncoder.matches(password, user.passwordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "用户名或密码错误");
        }
        return jwtService.issueUser(user);
    }
}
