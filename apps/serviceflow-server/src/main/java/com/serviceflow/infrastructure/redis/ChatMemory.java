package com.serviceflow.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.model.ChatModels;
import com.serviceflow.model.OrderModels;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service("serviceFlowChatMemory")
public final class ChatMemory {

    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final Duration REQUEST_LOCK_TTL = Duration.ofMinutes(2);
    private static final int MAX_HISTORY_SIZE = 20;
    private static final DefaultRedisScript<String> CLAIM_REQUEST = new DefaultRedisScript<>(
            """
            local completed = redis.call('GET', KEYS[1])
            if completed then return completed end
            if redis.call('SET', KEYS[2], ARGV[1], 'NX', 'PX', ARGV[2]) then return 'CLAIMED' end
            return 'IN_PROGRESS'
            """,
            String.class);
    private static final DefaultRedisScript<Long> COMPLETE_REQUEST = new DefaultRedisScript<>(
            """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] or redis.call('EXISTS', KEYS[4]) == 0 then return 0 end
            redis.call('RPUSH', KEYS[3], ARGV[3], ARGV[4])
            redis.call('LTRIM', KEYS[3], -tonumber(ARGV[6]), -1)
            redis.call('PEXPIRE', KEYS[3], ARGV[5])
            redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[5])
            redis.call('DEL', KEYS[1])
            return 1
            """,
            Long.class);
    private static final DefaultRedisScript<Long> RELEASE_REQUEST = new DefaultRedisScript<>(
            """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            return redis.call('DEL', KEYS[1])
            """,
            Long.class);
    private static final DefaultRedisScript<Long> UPDATE_COMPARISON = new DefaultRedisScript<>(
            """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
            return 1
            """,
            Long.class);

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

    public void deleteGuestSession(String subject, String id) {
        redis.delete(List.of(
                sessionKey(subject, id),
                memoryKey(subject, id),
                activeOrderKey(subject, id),
                activeProductKey(subject, id),
                pendingComparisonKey(subject, id)));
        redis.opsForZSet().remove(sessionIndexKey(subject), id);
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
        append(subject, sessionId, role, content, null);
    }

    public void append(String subject, String sessionId, String role, String content, String metadataJson) {
        try {
            String key = memoryKey(subject, sessionId);
            redis.opsForList().rightPush(key, messageJson(role, content, metadataJson));
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

    public GuestRequest claimGuestRequest(
            String subject, String sessionId, String clientRequestId, String executionId) {
        // 查完成结果和抢锁在同一脚本中执行，上一执行刚提交的结果会直接用于重放。
        String result = redis.execute(
                CLAIM_REQUEST,
                        List.of(
                                completedKey(subject, sessionId, clientRequestId),
                                requestKey(subject, sessionId, clientRequestId)),
                executionId, Long.toString(REQUEST_LOCK_TTL.toMillis()));
        if ("CLAIMED".equals(result)) return new GuestRequest(true, null);
        if ("IN_PROGRESS".equals(result)) return new GuestRequest(false, null);
        try {
            return new GuestRequest(false, objectMapper.readValue(result, CompletedResponse.class));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read completed response", exception);
        }
    }

    public boolean completeGuestRequest(
            String subject,
            String sessionId,
            String clientRequestId,
            String executionId,
            String userMessage,
            CompletedResponse response) {
        try {
            // 仅提交成功时写入这一轮的两条历史，避免失败重试留下重复的访客提问。
            return Long.valueOf(1)
                    .equals(redis.execute(
                            COMPLETE_REQUEST,
                            List.of(
                                    requestKey(subject, sessionId, clientRequestId),
                                            completedKey(subject, sessionId, clientRequestId),
                                    memoryKey(subject, sessionId), sessionKey(subject, sessionId)),
                            executionId,
                            objectMapper.writeValueAsString(response),
                            messageJson("USER", userMessage, null),
                            messageJson("ASSISTANT", response.answer(), response.metadataJson()),
                            Long.toString(SESSION_TTL.toMillis()),
                            Integer.toString(MAX_HISTORY_SIZE)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot encode completed response", exception);
        }
    }

    public void releaseGuestRequest(String subject, String sessionId, String clientRequestId, String executionId) {
        redis.execute(RELEASE_REQUEST, List.of(requestKey(subject, sessionId, clientRequestId)), executionId);
    }

    private String messageJson(String role, String content, String metadataJson) throws JsonProcessingException {
        return objectMapper.writeValueAsString(
                metadataJson == null
                        ? Map.of("role", role, "content", content)
                        : Map.of("role", role, "content", content, "metadataJson", metadataJson));
    }

    public void setActiveOrder(String subject, String sessionId, String orderNo) {
        redis.opsForValue().set(activeOrderKey(subject, sessionId), orderNo, SESSION_TTL);
    }

    public String activeOrder(String subject, String sessionId) {
        return redis.opsForValue().get(activeOrderKey(subject, sessionId));
    }

    public void clearActiveOrder(String subject, String sessionId) {
        redis.delete(activeOrderKey(subject, sessionId));
    }

    public void putOrderCandidates(String subject, String sessionId, OrderCandidates candidates) {
        try {
            redis.opsForValue()
                    .set(
                            orderCandidatesKey(subject, sessionId),
                            objectMapper.writeValueAsString(candidates),
                            SESSION_TTL);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot save order candidates", exception);
        }
    }

    public OrderCandidates orderCandidates(String subject, String sessionId) {
        String value = redis.opsForValue().get(orderCandidatesKey(subject, sessionId));
        if (value == null) return null;
        try {
            return objectMapper.readValue(value, OrderCandidates.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read order candidates", exception);
        }
    }

    public void clearOrderCandidates(String subject, String sessionId) {
        redis.delete(orderCandidatesKey(subject, sessionId));
    }

    private String orderCandidatesKey(String subject, String sessionId) {
        return "chat:order-candidates:" + subject + ':' + sessionId;
    }

    public record OrderCandidates(
            String queryId, List<OrderModels.Summary> items, boolean hasMore, boolean cancellation) {}

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

    public void clearActiveProduct(String subject, String sessionId) {
        redis.delete(activeProductKey(subject, sessionId));
    }

    public ChatModels.PendingComparison pendingComparison(String subject, String sessionId) {
        String value = redis.opsForValue().get(pendingComparisonKey(subject, sessionId));
        if (value == null) return null;
        try {
            return objectMapper.readValue(value, ChatModels.PendingComparison.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read pending comparison", exception);
        }
    }

    public void putPendingComparison(String subject, String sessionId, ChatModels.PendingComparison comparison) {
        redis.opsForValue().set(pendingComparisonKey(subject, sessionId), comparisonJson(comparison), SESSION_TTL);
    }

    public void clearPendingComparison(String subject, String sessionId) {
        redis.delete(pendingComparisonKey(subject, sessionId));
    }

    public boolean updatePendingComparison(
            String subject,
            String sessionId,
            ChatModels.PendingComparison expected,
            ChatModels.PendingComparison updated) {
        // Lua 原子校验旧快照、写入新快照并刷新有效期，避免并发补充覆盖彼此，也避免恢复已过期的比较。
        return Long.valueOf(1)
                .equals(redis.execute(
                        UPDATE_COMPARISON,
                        List.of(pendingComparisonKey(subject, sessionId)),
                        comparisonJson(expected),
                        comparisonJson(updated),
                        Long.toString(SESSION_TTL.toMillis())));
    }

    private String comparisonJson(ChatModels.PendingComparison comparison) {
        try {
            return objectMapper.writeValueAsString(comparison);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot encode pending comparison", exception);
        }
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

    private String requestKey(String subject, String sessionId, String clientRequestId) {
        return "chat:request:" + subject + ':' + sessionId + ':' + clientRequestId;
    }

    private String activeOrderKey(String subject, String sessionId) {
        return "chat:order:" + subject + ':' + sessionId;
    }

    private String activeProductKey(String subject, String sessionId) {
        return "chat:product:" + subject + ':' + sessionId;
    }

    private String pendingComparisonKey(String subject, String sessionId) {
        return "chat:comparison:" + subject + ':' + sessionId;
    }

    public record GuestRequest(boolean started, CompletedResponse completed) {}

    public record CompletedResponse(
            String messageId,
            String answer,
            String intent,
            List<String> citations,
            boolean degraded,
            String metadataJson) {}
}
