package com.serviceflow.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String DOCUMENT_EXCHANGE = "serviceflow.document";
    public static final String DOCUMENT_QUEUE = "serviceflow.document.ingest";
    public static final String DOCUMENT_DLQ = "serviceflow.document.ingest.dlq";
    public static final String ROUTING_KEY = "document.ingest";

    @Bean
    DirectExchange documentExchange() {
        return new DirectExchange(DOCUMENT_EXCHANGE, true, false);
    }

    @Bean
    Queue documentQueue() {
        return QueueBuilder.durable(DOCUMENT_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DOCUMENT_DLQ)
                .build();
    }

    @Bean
    Queue documentDlq() {
        return QueueBuilder.durable(DOCUMENT_DLQ).build();
    }

    @Bean
    Binding documentBinding(Queue documentQueue, DirectExchange documentExchange) {
        return BindingBuilder.bind(documentQueue).to(documentExchange).with(ROUTING_KEY);
    }
}
