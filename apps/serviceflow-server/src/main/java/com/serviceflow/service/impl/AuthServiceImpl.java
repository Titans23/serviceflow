package com.serviceflow.service.impl;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.serviceflow.mapper.UserMapper;
import com.serviceflow.model.AuthModels;
import com.serviceflow.security.JwtService;
import com.serviceflow.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthServiceImpl implements AuthService {
    private final UserMapper users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(UserMapper users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    public AuthModels.Token login(String username, String password) {
        AuthModels.User user = users.findByUsername(username);
        if (user == null || !user.enabled() || !passwordEncoder.matches(password, user.passwordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "用户名或密码错误");
        }
        return jwtService.issueUser(user);
    }
}
