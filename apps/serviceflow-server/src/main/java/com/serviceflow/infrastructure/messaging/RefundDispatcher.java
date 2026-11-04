package com.serviceflow.infrastructure.messaging;

import com.serviceflow.config.RabbitConfig;
import com.serviceflow.mapper.RefundDeliveryMapper;
import com.serviceflow.service.RefundDeliveryService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "serviceflow.refund.dispatch-enabled", havingValue = "true")
public class RefundDispatcher {
    private final RefundDeliveryMapper tasks;
    private final RefundDeliveryService delivery;
    private final RefundChannelClient channel;
    private final RabbitTemplate rabbit;
    private final MeterRegistry metrics;

    public RefundDispatcher(
            RefundDeliveryMapper tasks,
            RefundDeliveryService delivery,
            RefundChannelClient channel,
            RabbitTemplate rabbit,
            MeterRegistry metrics) {
        this.tasks = tasks;
        this.delivery = delivery;
        this.channel = channel;
        this.rabbit = rabbit;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${serviceflow.refund.poll-interval:2s}")
    @Transactional
    public void publishDue() {
        for (Long id : tasks.due()) {
            try {
                rabbit.invoke(operations -> {
                    operations.convertAndSend("", RabbitConfig.REFUND_QUEUE, id.toString());
                    operations.waitForConfirmsOrDie(5_000);
                    return null;
                });
                // A lost confirmation is harmless: the next scan republishes the same durable task.
                tasks.published(id);
                metrics.counter("serviceflow.refund.dispatch.published").increment();
            } catch (RuntimeException failure) {
                metrics.counter("serviceflow.refund.dispatch.publish_failures").increment();
                break;
            }
        }
    }

    @RabbitListener(queues = RabbitConfig.REFUND_QUEUE, concurrency = "2")
    public void process(String message) {
        long id = Long.parseLong(message);
        var claim = delivery.claim(id);
        if (claim == null) return;
        long started = System.nanoTime();
        try {
            delivery.queried(claim);
            var receipt = channel.query(claim.refund().publicId());
            // Only authoritative absence, or an explicitly failed older attempt, permits submission.
            boolean canSubmit = receipt == null
                    || ("FAILED".equals(receipt.status())
                            && receipt.attemptNo() < claim.task().attemptNo()
                            && claim.refund().publicId().equals(receipt.refundId())
                            && receipt.amount() != null
                            && receipt.amount().compareTo(claim.refund().amount()) == 0);
            if (canSubmit) {
                if (!claim.task().deadlineAt().isAfter(java.time.Instant.now())) {
                    delivery.waitOrEscalate(claim, "处理时限已到，渠道尚无受理记录");
                    return;
                }
                if (!delivery.beforeSubmit(claim)) return;
                receipt = channel.submit(claim.refund());
            }
            if (receipt != null && java.util.Set.of("SUCCEEDED", "FAILED").contains(receipt.status())) {
                delivery.accept(claim, receipt);
            } else {
                delivery.waitOrEscalate(claim, "渠道尚未返回本次退款的最终结果");
            }
        } catch (RuntimeException failure) {
            metrics.counter("serviceflow.refund.dispatch.unknown").increment();
            delivery.waitOrEscalate(claim, "渠道查询或提交结果未知，将先查询原退款编号");
        } finally {
            metrics.timer("serviceflow.refund.dispatch.duration")
                    .record(java.time.Duration.ofNanos(System.nanoTime() - started));
        }
    }
}
