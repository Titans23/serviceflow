package com.serviceflow.controller;

import static org.springframework.http.HttpStatus.FORBIDDEN;

import com.serviceflow.infrastructure.redis.GuestRateLimiter;
import com.serviceflow.model.ChatModels;
import com.serviceflow.security.CurrentPrincipal;
import com.serviceflow.service.ChatActionService;
import com.serviceflow.service.ChatService;
import com.serviceflow.service.ChatSessionService;
import com.serviceflow.web.sse.SseEventWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/chat")
public final class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final ChatSessionService sessions;
    private final ChatService chatService;
    private final ChatActionService actions;
    private final GuestRateLimiter limiter;
    private final SseEventWriter events;

    public ChatController(
            ChatSessionService sessions,
            ChatService chatService,
            ChatActionService actions,
            GuestRateLimiter limiter,
            SseEventWriter events) {
        this.sessions = sessions;
        this.chatService = chatService;
        this.actions = actions;
        this.limiter = limiter;
        this.events = events;
    }

    @PostMapping("/sessions")
    ChatModels.Session create(
            @RequestBody(required = false) ChatModels.CreateSessionRequest request, Authentication authentication) {
        return sessions.create(CurrentPrincipal.from(authentication), request == null ? null : request.title());
    }

    @GetMapping("/sessions")
    List<ChatModels.Session> sessions(Authentication authentication) {
        return sessions.list(CurrentPrincipal.from(authentication));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    List<ChatModels.Message> messages(@PathVariable String sessionId, Authentication authentication) {
        return sessions.messages(CurrentPrincipal.from(authentication), sessionId);
    }

    /**
     * 建立一次最长 120 秒的 SSE 连接。
     *
     * <p>Controller 立即把 emitter 交给 Spring，虚拟线程随后执行耗时的聊天流程，并通过同一个 emitter 分批发送事件。
     */
    @PostMapping(value = "/sessions/{sessionId}/messages/stream", produces = "text/event-stream")
    SseEmitter stream(
            @PathVariable String sessionId,
            @Valid @RequestBody ChatModels.MessageRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        CurrentPrincipal principal = CurrentPrincipal.from(authentication);

        // 访客没有账号级配额，因此先按访客标识和来源 IP 做请求频率限制。
        if (principal.guest()) limiter.check(principal.subject(), httpRequest.getRemoteAddr());

        // emitter 代表服务端可持续写入的 HTTP 响应通道；超时后连接会被关闭。
        SseEmitter emitter = new SseEmitter(120_000L);
        events.track(emitter);

        // 将阻塞式 AI 调用交给廉价的虚拟线程，避免长期占用当前 Tomcat 请求线程。
        Thread.startVirtualThread(() -> run(emitter, () -> chatService.stream(principal, sessionId, request, emitter)));

        // 返回 emitter 后 HTTP 响应不会立刻结束，后续事件仍可由虚拟线程写入。
        return emitter;
    }

    @PostMapping(value = "/actions/{actionId}/confirm", produces = "text/event-stream")
    SseEmitter confirm(
            @PathVariable String actionId,
            @Valid @RequestBody ChatModels.ConfirmRequest request,
            Authentication authentication) {
        CurrentPrincipal principal = CurrentPrincipal.from(authentication);
        if (principal.guest()) {
            throw new ResponseStatusException(FORBIDDEN, "访客不能执行该操作");
        }
        SseEmitter emitter = new SseEmitter(60_000L);
        events.track(emitter);
        Thread.startVirtualThread(
                () -> run(emitter, () -> actions.confirm(principal, actionId, request.decision(), emitter)));
        return emitter;
    }

    /**
     * 在虚拟线程中执行 SSE 业务操作，并把无法作为普通 HTTP 响应返回的异常转换成 SSE error 事件。
     */
    private void run(SseEmitter emitter, Runnable operation) {
        try {
            operation.run();
        } catch (Exception exception) {
            log.error("Chat SSE operation failed", exception);
            try {
                if (exception instanceof com.serviceflow.exception.ServiceFlowException serviceFlowException) {
                    // 已知业务异常保留错误码、提示和是否可重试信息。
                    events.error(
                            emitter,
                            serviceFlowException.code(),
                            serviceFlowException.getMessage(),
                            serviceFlowException.retryable());
                } else {
                    // 未知异常不向前端暴露内部细节，统一转换成 INTERNAL_ERROR。
                    String message = exception instanceof ResponseStatusException status && status.getReason() != null
                            ? status.getReason()
                            : "服务暂时不可用，请稍后重试";
                    events.error(emitter, com.serviceflow.exception.ErrorCode.INTERNAL_ERROR, message, true);
                }
            } catch (Exception sendFailure) {
                // 浏览器可能已经断开，此时连 error 事件也无法发送，只记录日志。
                log.debug("Unable to send SSE error because the connection is closed", sendFailure);
            } finally {
                // 无论 error 事件是否发送成功，都结束服务器端的 SSE 响应。
                emitter.complete();
            }
        }
    }
}
