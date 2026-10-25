package com.serviceflow.ai;

import com.serviceflow.config.EvaluationProviderBudget;
import com.serviceflow.exception.ErrorCode;
import com.serviceflow.exception.ServiceFlowException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public final class AiCallExecutor {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final MeterRegistry metrics;
    private final EvaluationProviderBudget evaluationBudget;

    public AiCallExecutor(MeterRegistry metrics) {
        this(metrics, (EvaluationProviderBudget) null);
    }

    @Autowired
    public AiCallExecutor(MeterRegistry metrics, ObjectProvider<EvaluationProviderBudget> budget) {
        this(metrics, budget.getIfAvailable());
    }

    private AiCallExecutor(MeterRegistry metrics, EvaluationProviderBudget budget) {
        this.metrics = metrics;
        this.evaluationBudget = budget;
    }

    public <T> T execute(String operation, Duration timeout, Supplier<T> call) {
        long started = System.nanoTime();
        String status = "success";
        try {
            return executor.submit(call::get).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            if (evaluationBudget != null) evaluationBudget.stopAfterFailure("model_timeout");
            status = "timeout";
            metrics.counter("serviceflow.ai.timeouts", "operation", operation).increment();
            throw new ServiceFlowException(HttpStatus.GATEWAY_TIMEOUT, ErrorCode.MODEL_TIMEOUT, "模型服务响应超时", true);
        } catch (InterruptedException exception) {
            if (evaluationBudget != null) evaluationBudget.stopAfterFailure("model_interrupted");
            status = "interrupted";
            Thread.currentThread().interrupt();
            throw new ServiceFlowException(
                    HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.MODEL_UNAVAILABLE, "模型调用被中断", true);
        } catch (ExecutionException exception) {
            if (evaluationBudget != null) evaluationBudget.stopAfterFailure("model_transport");
            status = "failure";
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new ServiceFlowException(
                    HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.MODEL_UNAVAILABLE, "模型服务暂时不可用", true);
        } finally {
            metrics.timer("serviceflow.ai.duration", "operation", operation, "status", status)
                    .record(Duration.ofNanos(System.nanoTime() - started));
        }
    }

    @PreDestroy
    void close() {
        executor.close();
    }
}
