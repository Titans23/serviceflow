package com.serviceflow.infrastructure.messaging;

import com.serviceflow.config.RabbitConfig;
import com.serviceflow.mapper.KnowledgeMapper;
import com.serviceflow.model.KnowledgeModels;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class KnowledgeOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeOutboxPublisher.class);
    private static final int BATCH_SIZE = 50;

    private final KnowledgeMapper mapper;
    private final RabbitTemplate rabbit;
    private final MeterRegistry metrics;

    public KnowledgeOutboxPublisher(KnowledgeMapper mapper, RabbitTemplate rabbit, MeterRegistry metrics) {
        this.mapper = mapper;
        this.rabbit = rabbit;
        this.metrics = metrics;
        metrics.gauge("serviceflow.outbox.pending", mapper, KnowledgeMapper::pendingOutboxCount);
    }

    @Scheduled(fixedDelayString = "${serviceflow.outbox.poll-interval:2s}")
    @Transactional
    public void publishDue() {
        for (KnowledgeModels.OutboxEvent event : mapper.dueOutbox(BATCH_SIZE)) {
            try {
                rabbit.invoke(operations -> {
                    operations.convertAndSend(
                            RabbitConfig.DOCUMENT_EXCHANGE, RabbitConfig.ROUTING_KEY, event.documentVersionId());
                    operations.waitForConfirmsOrDie(5_000);
                    return null;
                });
                mapper.markOutboxPublished(event.id());
                metrics.counter("serviceflow.outbox.published").increment();
                metrics.timer("serviceflow.outbox.lag")
                        .record(java.time.Duration.between(event.createdAt(), Instant.now()));
            } catch (Exception exception) {
                long delaySeconds = Math.min(300, 1L << Math.min(event.attempts() + 1, 8));
                String message = safeMessage(exception);
                mapper.markOutboxFailed(event.id(), message, Instant.now().plus(delaySeconds, ChronoUnit.SECONDS));
                metrics.counter("serviceflow.outbox.failed").increment();
                log.warn("Knowledge outbox publish failed for version {}", event.documentVersionId(), exception);
            }
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return message.substring(0, Math.min(message.length(), 500));
    }
}
