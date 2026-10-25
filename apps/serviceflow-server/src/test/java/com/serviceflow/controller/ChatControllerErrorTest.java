package com.serviceflow.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.serviceflow.exception.ErrorCode;
import com.serviceflow.exception.ServiceFlowException;
import com.serviceflow.web.sse.SseEventWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ChatControllerErrorTest {
    private final SseEventWriter events = mock(SseEventWriter.class);
    private final SseEmitter emitter = mock(SseEmitter.class);
    private final ChatController controller = new ChatController(null, null, null, null, events);

    @ParameterizedTest
    @CsvSource({"403,FORBIDDEN", "404,NOT_FOUND", "409,CONFLICT", "400,BAD_REQUEST"})
    void keepsBusinessDenialsNonRetryableAcrossWorkflowWrappers(int status, ErrorCode expected) {
        run(new IllegalStateException(
                "workflow wrapper",
                new IllegalStateException(
                        "graph wrapper", new ResponseStatusException(HttpStatus.valueOf(status), "业务拒绝"))));
        verify(events).error(emitter, expected, "业务拒绝", false);
        verify(emitter).complete();
    }

    @Test
    void expiredConfirmationHasStableNonRetryableCode() {
        run(new ResponseStatusException(HttpStatus.NOT_FOUND, "操作已过期"));
        verify(events).error(emitter, ErrorCode.NOT_FOUND, "操作已过期", false);
    }

    @Test
    void preservesExplicitModelTimeoutRetryability() {
        run(new IllegalStateException(
                "wrapper",
                new ServiceFlowException(HttpStatus.GATEWAY_TIMEOUT, ErrorCode.MODEL_TIMEOUT, "模型服务响应超时", true)));
        verify(events).error(emitter, ErrorCode.MODEL_TIMEOUT, "模型服务响应超时", true);
    }

    @Test
    void unknownFailureDoesNotExposeInternalMessage() {
        run(new IllegalStateException("private diagnostic detail"));
        verify(events).error(emitter, ErrorCode.INTERNAL_ERROR, "服务暂时不可用，请稍后重试", true);
    }

    private void run(RuntimeException failure) {
        ReflectionTestUtils.invokeMethod(controller, "run", emitter, (Runnable) () -> {
            throw failure;
        });
    }
}
