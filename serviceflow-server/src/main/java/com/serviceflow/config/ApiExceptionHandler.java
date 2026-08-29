package com.serviceflow.config;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public final class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, Object>> status(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(body(
                        exception.getStatusCode().toString(),
                        exception.getReason() == null ? "请求失败" : exception.getReason(),
                        false));
    }

    @ExceptionHandler(ServiceFlowException.class)
    ResponseEntity<Map<String, Object>> serviceFlow(ServiceFlowException exception) {
        return ResponseEntity.status(exception.status())
                .body(body(exception.code().name(), exception.getMessage(), exception.retryable()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException exception) {
        FieldError error = exception.getBindingResult().getFieldErrors().getFirst();
        return ResponseEntity.badRequest()
                .body(body("VALIDATION_ERROR", error.getField() + ": " + error.getDefaultMessage(), false));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unknown(Exception exception) {
        log.error("Unhandled API exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body("INTERNAL_ERROR", "服务暂时不可用", true));
    }

    private Map<String, Object> body(String code, String message, boolean retryable) {
        return Map.of(
                "code",
                code,
                "message",
                message,
                "retryable",
                retryable,
                "traceId",
                traceId(),
                "timestamp",
                Instant.now().toString(),
                "fieldErrors",
                List.of());
    }

    private String traceId() {
        String value = MDC.get("traceId");
        return value == null ? "" : value;
    }
}
