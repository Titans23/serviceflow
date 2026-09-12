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

    /**
     * 为登录用户登记一次聊天请求，并决定当前调用能否获得执行权。
     *
     * <p>事务保证“登记请求、保存用户消息、关联两者”要么全部成功，要么一起回滚，避免留下不完整数据。
     */
    @Transactional
    public BeginResult begin(long sessionId, String clientRequestId, String message) {
        // 数据库唯一约束使同一会话中的 clientRequestId 只能插入一次；插入成功者获得首次执行权。
        if (chats.insertRequest(sessionId, clientRequestId) == 1) {
            String userMessageId = UUID.randomUUID().toString();
            chats.insertMessage(userMessageId, sessionId, null, "USER", message, null, "{}");
            chats.attachUserMessage(sessionId, clientRequestId, userMessageId);
            return BeginResult.startedResult();
        }

        // 重复请求若已经完成，则读取并返回旧答案，由上层通过 SSE 重放，避免再次调用 AI。
        ChatMapper.RequestRow request = chats.request(sessionId, clientRequestId);
        if (request != null && "COMPLETED".equals(request.status())) {
            ChatModels.Message completed = chats.completed(sessionId, clientRequestId);
            if (completed != null) {
                return BeginResult.completedResult(completed);
            }
        }

        // PROCESSING 超过阈值通常意味着原执行者异常退出；条件更新成功的调用获得恢复执行权。
        Instant staleBefore = Instant.now().minus(STALE_AFTER);
        if (chats.restartRequest(sessionId, clientRequestId, staleBefore) == 1) {
            return BeginResult.startedResult();
        }

        // 请求仍由其他线程处理时明确返回冲突，防止两个 AI 调用同时生成同一条回答。
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
