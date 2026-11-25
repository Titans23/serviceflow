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
        // 按请求键锁定当前行。写入的执行标识用于区分首次插入和并发提交。
        String executionId = UUID.randomUUID().toString();
        chats.insertRequest(sessionId, clientRequestId, executionId);
        ChatMapper.RequestRow request = chats.request(sessionId, clientRequestId);
        if (executionId.equals(request.executionId())) {
            String userMessageId = UUID.randomUUID().toString();
            chats.insertMessage(userMessageId, sessionId, null, "USER", message, null, "{}");
            chats.attachUserMessage(sessionId, clientRequestId, userMessageId);
            return new BeginResult(executionId, true, null);
        }

        // 重复请求若已经完成，则读取并返回旧答案，由上层通过 SSE 重放，避免再次调用 AI。
        if ("COMPLETED".equals(request.status())) {
            ChatModels.Message completed = chats.completed(sessionId, clientRequestId);
            if (completed != null) {
                return new BeginResult(null, false, completed);
            }
        }

        // PROCESSING 超过阈值通常意味着原执行者异常退出；条件更新成功的调用获得恢复执行权。
        Instant staleBefore = Instant.now().minus(STALE_AFTER);
        if (chats.restartRequest(sessionId, clientRequestId, executionId, staleBefore) == 1) {
            return new BeginResult(executionId, false, null);
        }

        // 请求仍由其他线程处理时明确返回冲突，防止两个 AI 调用同时生成同一条回答。
        throw inProgress();
    }

    @Transactional
    public void complete(
            long sessionId,
            String clientRequestId,
            String executionId,
            String messageId,
            String answer,
            String intent,
            String metadata) {
        // 先锁定并校验执行权，写入消息失败时请求状态随事务回滚。
        if (chats.completeRequest(sessionId, clientRequestId, executionId, messageId) != 1) throw inProgress();
        chats.insertMessage(messageId, sessionId, clientRequestId, "ASSISTANT", answer, intent, metadata);
    }

    public void fail(long sessionId, String clientRequestId, String executionId, String errorCode) {
        chats.failRequest(sessionId, clientRequestId, executionId, errorCode);
    }

    public static ServiceFlowException inProgress() {
        return new ServiceFlowException(HttpStatus.CONFLICT, ErrorCode.REQUEST_IN_PROGRESS, "请求正在处理中", true);
    }

    public record BeginResult(String executionId, boolean firstExecution, ChatModels.Message completed) {
        public boolean started() {
            return executionId != null;
        }
    }
}
