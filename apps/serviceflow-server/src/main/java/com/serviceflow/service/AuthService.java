package com.serviceflow.service;

import com.serviceflow.model.AuthModels;

public interface AuthService {
    AuthModels.Token login(String username, String password);
}
