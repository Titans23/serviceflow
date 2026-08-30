package com.serviceflow.web.sse;

import com.serviceflow.exception.ErrorCode;
import com.serviceflow.exception.ServiceFlowException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public final class SseEventWriter {
    private final MeterRegistry metrics;

    public SseEventWriter(MeterRegistry metrics) {
        this.metrics = metrics;
        metrics.gauge("serviceflow.sse.active", ActiveConnections.INSTANCE, ActiveConnections::count);
    }

    public Connection track(SseEmitter emitter) {
        metrics.counter("serviceflow.sse.opened").increment();
        ActiveConnections.INSTANCE.increment();
        Connection connection = new Connection(emitter);
        emitter.onCompletion(connection::close);
        emitter.onTimeout(connection::close);
        emitter.onError(ignored -> connection.close());
        return connection;
    }

    public void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception exception) {
            throw new ServiceFlowException(
                    HttpStatus.GONE, ErrorCode.SSE_CONNECTION_CLOSED, "SSE connection closed", false);
        }
    }

    public void error(SseEmitter emitter, ErrorCode code, String message, boolean retryable) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code.name());
        payload.put("message", message);
        payload.put("retryable", retryable);
        payload.put("traceId", traceId());
        send(emitter, "error", payload);
    }

    public String traceId() {
        String value = MDC.get("traceId");
        return value == null ? "" : value;
    }

    public final class Connection {
        private final SseEmitter emitter;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Connection(SseEmitter emitter) {
            this.emitter = emitter;
        }

        public void complete() {
            emitter.complete();
            close();
        }

        private void close() {
            if (closed.compareAndSet(false, true)) {
                ActiveConnections.INSTANCE.decrement();
            }
        }
    }

    private enum ActiveConnections {
        INSTANCE;

        private final java.util.concurrent.atomic.AtomicInteger value = new java.util.concurrent.atomic.AtomicInteger();

        void increment() {
            value.incrementAndGet();
        }

        void decrement() {
            value.updateAndGet(current -> Math.max(0, current - 1));
        }

        double count() {
            return value.doubleValue();
        }
    }
}
