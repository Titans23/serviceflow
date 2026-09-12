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

    /**
     * 开始跟踪一条 SSE 连接，并注册完成、超时和异常三种关闭回调。
     *
     * <p>这些回调可能先后触发，因此 Connection.close() 必须保证活跃连接数只减少一次。
     */
    public Connection track(SseEmitter emitter) {
        metrics.counter("serviceflow.sse.opened").increment();
        ActiveConnections.INSTANCE.increment();
        Connection connection = new Connection(emitter);
        emitter.onCompletion(connection::close);
        emitter.onTimeout(connection::close);
        emitter.onError(ignored -> connection.close());
        return connection;
    }

    /** 将事件名称和数据写入仍然打开的 SSE 响应通道。 */
    public void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception exception) {
            // 常见原因是浏览器关闭页面或网络断开，此时后端不能再向该连接写数据。
            throw new ServiceFlowException(
                    HttpStatus.GONE, ErrorCode.SSE_CONNECTION_CLOSED, "SSE connection closed", false);
        }
    }

    /** 以统一的 error 事件格式把业务错误发送给前端。 */
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

    /** 封装连接的主动完成操作，并保证连接指标只被关闭一次。 */
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
            // compareAndSet 只有第一次能把 false 改成 true，避免多个关闭回调重复减计数。
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
