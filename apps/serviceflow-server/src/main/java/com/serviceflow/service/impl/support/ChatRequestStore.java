package com.serviceflow.service.impl.support;

import com.serviceflow.exception.ErrorCode;
import com.serviceflow.exception.ServiceFlowException;
import com.serviceflow.mapper.ChatMapper;
import com.serviceflow.model.ChatModels;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatRequestStore {
    private static final Duration STALE_AFTER = Duration.ofMinutes(2);

    private final ChatMapper chats;

    public ChatRequestStore(ChatMapper chats) {
        this.chats = chats;
    }

    @Transactional
    public BeginResult begin(long sessionId, String clientRequestId, String message) {
        if (chats.insertRequest(sessionId, clientRequestId) == 1) {
            String userMessageId = UUID.randomUUID().toString();
            chats.insertMessage(userMessageId, sessionId, null, "USER", message, null, "{}");
            chats.attachUserMessage(sessionId, clientRequestId, userMessageId);
            return BeginResult.startedResult();
        }

        ChatMapper.RequestRow request = chats.request(sessionId, clientRequestId);
        if (request != null && "COMPLETED".equals(request.status())) {
            ChatModels.Message completed = chats.completed(sessionId, clientRequestId);
            if (completed != null) {
                return BeginResult.completedResult(completed);
            }
        }

        Instant staleBefore = Instant.now().minus(STALE_AFTER);
        if (chats.restartRequest(sessionId, clientRequestId, staleBefore) == 1) {
            return BeginResult.startedResult();
        }
        throw new ServiceFlowException(HttpStatus.CONFLICT, ErrorCode.REQUEST_IN_PROGRESS, "请求正在处理中", true);
    }

    @Transactional
    public void complete(
            long sessionId, String clientRequestId, String messageId, String answer, String intent, String metadata) {
        chats.insertMessage(messageId, sessionId, clientRequestId, "ASSISTANT", answer, intent, metadata);
        if (chats.completeRequest(sessionId, clientRequestId, messageId) != 1) {
            throw new IllegalStateException("Chat request was not PROCESSING during completion");
        }
    }

    public void fail(long sessionId, String clientRequestId, String errorCode) {
        chats.failRequest(sessionId, clientRequestId, errorCode);
    }

    public record BeginResult(boolean started, ChatModels.Message completed) {
        static BeginResult startedResult() {
            return new BeginResult(true, null);
        }

        static BeginResult completedResult(ChatModels.Message message) {
            return new BeginResult(false, message);
        }
    }
}
