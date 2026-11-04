package com.serviceflow.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.serviceflow.exception.ErrorCode;
import com.serviceflow.exception.ServiceFlowException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AiCallExecutorTimeoutTest {
    @Test
    void callerInterruptionCancelsTheSubmittedWorkAndPreservesInterruptFlag() throws Exception {
        var started = new CountDownLatch(1);
        var stopped = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var interrupted = new java.util.concurrent.atomic.AtomicBoolean();
        var calls = new AiCallExecutor(new SimpleMeterRegistry());
        Thread caller = Thread.startVirtualThread(() -> {
            try {
                calls.execute("interrupt-probe", Duration.ofSeconds(30), () -> {
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    } finally {
                        stopped.countDown();
                    }
                    return null;
                });
            } catch (ServiceFlowException failure) {
                interrupted.set(failure.code() == ErrorCode.MODEL_UNAVAILABLE
                        && Thread.currentThread().isInterrupted());
            }
        });
        try {
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            caller.interrupt();
            caller.join(2000);
            assertThat(stopped.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(interrupted).isTrue();
        } finally {
            release.countDown();
            caller.interrupt();
            calls.close();
        }
    }

    @Test
    void timedOutInterruptibleCallsReleaseTheirWorkBeforeTheNextWave() throws Exception {
        var active = new AtomicInteger();
        var peak = new AtomicInteger();
        var timeouts = new AtomicInteger();
        var release = new CountDownLatch(1);
        var calls = new AiCallExecutor(new SimpleMeterRegistry());
        var latencies = new java.util.concurrent.ConcurrentLinkedQueue<Long>();
        try (var callers = Executors.newFixedThreadPool(20)) {
            calls.execute("probe", Duration.ofSeconds(1), () -> "warm");
            for (int wave = 0; wave < 2; wave++) {
                var jobs = new ArrayList<java.util.concurrent.Future<?>>();
                for (int i = 0; i < 20; i++) {
                    jobs.add(callers.submit(() -> {
                        long started = System.nanoTime();
                        try {
                            calls.execute("probe", Duration.ofMillis(150), () -> {
                                int current = active.incrementAndGet();
                                peak.accumulateAndGet(current, Math::max);
                                try {
                                    release.await();
                                    return "released";
                                } catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                    return "cancelled";
                                } finally {
                                    active.decrementAndGet();
                                }
                            });
                        } catch (ServiceFlowException failure) {
                            assertThat(failure.code()).isEqualTo(ErrorCode.MODEL_TIMEOUT);
                            timeouts.incrementAndGet();
                        } finally {
                            latencies.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                        }
                    }));
                }
                for (var job : jobs) job.get(10, TimeUnit.SECONDS);
            }
            long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
            while (active.get() != 0 && System.nanoTime() < deadline) Thread.sleep(5);
            var sorted = latencies.stream().sorted().toList();
            System.out.println("TIMEOUT_PROBE {\"calls\":40,\"timeouts\":" + timeouts.get()
                    + ",\"activeAfterTimeout\":" + active.get() + ",\"peakActive\":" + peak.get()
                    + ",\"p50Ms\":" + sorted.get(19) + ",\"p95Ms\":" + sorted.get(37)
                    + ",\"p99Ms\":" + sorted.get(39) + "}");
            assertThat(timeouts).hasValue(40);
            assertThat(active).hasValue(0);
        } finally {
            release.countDown();
            calls.close();
        }
    }
}
