package com.serviceflow.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service("serviceFlowChatMemory")
public final class ChatMemory {

    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final Duration ACTION_TTL = Duration.ofMinutes(10);
    private static final int MAX_HISTORY_SIZE = 20;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public ChatMemory(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public String createGuestSession(String subject, String title) {
        String id = UUID.randomUUID().toString();
        String sessionTitle = title == null || title.isBlank() ? "商品咨询" : title;
        redis.opsForValue().set(sessionKey(subject, id), sessionTitle, SESSION_TTL);
        redis.opsForZSet().add(sessionIndexKey(subject), id, System.currentTimeMillis());
        redis.expire(sessionIndexKey(subject), SESSION_TTL);
        return id;
    }

    public boolean ownsGuestSession(String subject, String sessionId) {
        return Boolean.TRUE.equals(redis.hasKey(sessionKey(subject, sessionId)));
    }

    public List<ChatModels.Session> guestSessions(String subject) {
        Set<String> ids = redis.opsForZSet().reverseRange(sessionIndexKey(subject), 0, -1);
        if (ids == null) {
            return List.of();
        }
        Instant now = Instant.now();
        return ids.stream()
                .filter(id -> ownsGuestSession(subject, id))
                .map(id -> new ChatModels.Session(id, redis.opsForValue().get(sessionKey(subject, id)), now, now))
                .toList();
    }

    public void append(String subject, String sessionId, String role, String content) {
        try {
            String key = memoryKey(subject, sessionId);
            redis.opsForList()
                    .rightPush(key, objectMapper.writeValueAsString(Map.of("role", role, "content", content)));
            redis.opsForList().trim(key, -MAX_HISTORY_SIZE, -1);
            redis.expire(key, SESSION_TTL);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot store chat memory", exception);
        }
    }

    public List<Map<String, String>> history(String subject, String sessionId) {
        List<String> values = redis.opsForList().range(memoryKey(subject, sessionId), 0, -1);
        if (values == null) {
            return List.of();
        }
        return values.stream().map(this::deserializeMessage).toList();
    }

    public void storeCompleted(String subject, String sessionId, String clientRequestId, CompletedResponse response) {
        try {
            redis.opsForValue()
                    .set(
                            completedKey(subject, sessionId, clientRequestId),
                            objectMapper.writeValueAsString(response),
                            SESSION_TTL);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot store completed response", exception);
        }
    }

    public CompletedResponse completed(String subject, String sessionId, String clientRequestId) {
        String value = redis.opsForValue().get(completedKey(subject, sessionId, clientRequestId));
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, CompletedResponse.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read completed response", exception);
        }
    }

    public void setActiveOrder(String subject, String sessionId, String orderNo) {
        redis.opsForValue().set(activeOrderKey(subject, sessionId), orderNo, SESSION_TTL);
    }

    public String activeOrder(String subject, String sessionId) {
        return redis.opsForValue().get(activeOrderKey(subject, sessionId));
    }

    public void setActiveProduct(String subject, String sessionId, long productId) {
        redis.opsForValue().set(activeProductKey(subject, sessionId), Long.toString(productId), SESSION_TTL);
    }

    public Long activeProduct(String subject, String sessionId) {
        String value = redis.opsForValue().get(activeProductKey(subject, sessionId));
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            redis.delete(activeProductKey(subject, sessionId));
            return null;
        }
    }

    public String putAction(PendingAction action) {
        try {
            String id = UUID.randomUUID().toString();
            redis.opsForValue().set(actionKey(id), objectMapper.writeValueAsString(action), ACTION_TTL);
            return id;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot store pending action", exception);
        }
    }

    public PendingAction action(String id) {
        String value = redis.opsForValue().get(actionKey(id));
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, PendingAction.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read pending action", exception);
        }
    }

    public void removeAction(String id) {
        redis.delete(actionKey(id));
    }

    private Map<String, String> deserializeMessage(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read chat memory", exception);
        }
    }

    private String sessionKey(String subject, String id) {
        return "guest:session:" + subject + ':' + id;
    }

    private String sessionIndexKey(String subject) {
        return "guest:sessions:" + subject;
    }

    private String memoryKey(String subject, String id) {
        return "chat:memory:" + subject + ':' + id;
    }

    private String completedKey(String subject, String sessionId, String clientRequestId) {
        return "chat:completed:" + subject + ':' + sessionId + ':' + clientRequestId;
    }

    private String activeOrderKey(String subject, String sessionId) {
        return "chat:order:" + subject + ':' + sessionId;
    }

    private String activeProductKey(String subject, String sessionId) {
        return "chat:product:" + subject + ':' + sessionId;
    }

    private String actionKey(String id) {
        return "chat:action:" + id;
    }

    public record PendingAction(
            String actionType, String subject, long customerId, String sessionId, String orderNo, String query) {}

    public record CompletedResponse(
            String messageId, String answer, String intent, List<String> citations, boolean degraded) {}
}
