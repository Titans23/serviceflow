package com.serviceflow.config;

import org.springframework.http.HttpStatus;

public final class ServiceFlowException extends RuntimeException {
    private final HttpStatus status;
    private final ErrorCode code;
    private final boolean retryable;

    public ServiceFlowException(HttpStatus status, ErrorCode code, String message, boolean retryable) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryable = retryable;
    }

    public HttpStatus status() {
        return status;
    }

    public ErrorCode code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
