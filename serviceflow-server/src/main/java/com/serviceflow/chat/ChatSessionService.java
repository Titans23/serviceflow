package com.serviceflow.chat;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.serviceflow.auth.CurrentPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public final class ChatSessionService {
    private static final String DEFAULT_TITLE = "新会话";
    private static final int MAX_TITLE_LENGTH = 160;

    private final ChatMapper chats;
    private final ChatMemory memory;

    public ChatSessionService(ChatMapper chats, ChatMemory memory) {
        this.chats = chats;
        this.memory = memory;
    }

    public ChatModels.Session create(CurrentPrincipal principal, String title) {
        String safeTitle = normalizeTitle(title);
        if (principal.guest()) {
            String sessionId = memory.createGuestSession(principal.subject(), safeTitle);
            Instant now = Instant.now();
            return new ChatModels.Session(sessionId, safeTitle, now, now);
        }
        String sessionId = UUID.randomUUID().toString();
        chats.insertSession(sessionId, principal.requireCustomerId(), safeTitle);
        return chats.findSession(sessionId, principal.requireCustomerId());
    }

    public List<ChatModels.Session> list(CurrentPrincipal principal) {
        return principal.guest()
                ? memory.guestSessions(principal.subject())
                : chats.listSessions(principal.requireCustomerId());
    }

    public List<ChatModels.Message> messages(CurrentPrincipal principal, String sessionId) {
        if (!principal.guest()) {
            return chats.messages(requireCustomerSession(principal, sessionId));
        }
        requireGuestSession(principal, sessionId);
        Instant now = Instant.now();
        return memory.history(principal.subject(), sessionId).stream()
                .map(item -> new ChatModels.Message(
                        UUID.randomUUID().toString(), item.get("role"), item.get("content"), null, "{}", now))
                .toList();
    }

    public Long requireCustomerSession(CurrentPrincipal principal, String sessionId) {
        Long id = chats.sessionDatabaseId(sessionId, principal.requireCustomerId());
        if (id == null) {
            throw new ResponseStatusException(NOT_FOUND, "会话不存在");
        }
        return id;
    }

    public void requireGuestSession(CurrentPrincipal principal, String sessionId) {
        if (!memory.ownsGuestSession(principal.subject(), sessionId)) {
            throw new ResponseStatusException(NOT_FOUND, "访客会话不存在或已过期");
        }
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return DEFAULT_TITLE;
        }
        String normalized = title.trim();
        return normalized.substring(0, Math.min(normalized.length(), MAX_TITLE_LENGTH));
    }
}
