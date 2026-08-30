package com.serviceflow.service;

import com.serviceflow.model.ChatModels;
import com.serviceflow.security.CurrentPrincipal;
import java.util.List;

public interface ChatSessionService {
    ChatModels.Session create(CurrentPrincipal principal, String title);

    List<ChatModels.Session> list(CurrentPrincipal principal);

    List<ChatModels.Message> messages(CurrentPrincipal principal, String sessionId);

    Long requireCustomerSession(CurrentPrincipal principal, String sessionId);

    void requireGuestSession(CurrentPrincipal principal, String sessionId);
}
