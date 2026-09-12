package com.serviceflow.service.impl;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.serviceflow.infrastructure.redis.ChatMemory;
import com.serviceflow.mapper.ChatMapper;
import com.serviceflow.model.ChatModels;
import com.serviceflow.security.CurrentPrincipal;
import com.serviceflow.service.ChatSessionService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public final class ChatSessionServiceImpl implements ChatSessionService {
    private static final String DEFAULT_TITLE = "新会话";
    private static final int MAX_TITLE_LENGTH = 160;

    private final ChatMapper chats;
    private final ChatMemory memory;

    public ChatSessionServiceImpl(ChatMapper chats, ChatMemory memory) {
        this.chats = chats;
        this.memory = memory;
    }

    @Override
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

    @Override
    public List<ChatModels.Session> list(CurrentPrincipal principal) {
        return principal.guest()
                ? memory.guestSessions(principal.subject())
                : chats.listSessions(principal.requireCustomerId());
    }

    @Override
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

    /**
     * 校验登录用户是否拥有指定会话，并将对外使用的会话 UUID 转成数据库内部主键。
     *
     * <p>查询同时使用 chat_session.public_id 和当前用户的 customer_id，既完成 ID 转换，也防止用户访问他人的会话。
     */
    @Override
    public Long requireCustomerSession(CurrentPrincipal principal, String sessionId) {
        // sessionId 来自请求 URL，对应 chat_session.public_id；返回值对应 chat_session.id。
        Long id = chats.sessionDatabaseId(sessionId, principal.requireCustomerId());
        if (id == null) {
            throw new ResponseStatusException(NOT_FOUND, "会话不存在");
        }
        return id;
    }

    @Override
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
