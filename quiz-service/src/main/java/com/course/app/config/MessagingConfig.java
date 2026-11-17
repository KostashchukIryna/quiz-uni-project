package com.course.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Events are sent as JSON built from the shared {@code QuizEvent}. The exchange and its queues are provisioned by
 * the broker (see rabbitmq/definitions.json), so this service only needs the exchange name.
 */
@Configuration
public class MessagingConfig {

    private static final Logger log = LoggerFactory.getLogger(MessagingConfig.class);

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /** An event the broker could not route or did not confirm is logged, never silently dropped. */
    @Bean
    public RabbitTemplateCustomizer publishDiagnostics() {
        return (RabbitTemplate template) -> {
            template.setReturnsCallback(returned -> log.error(
                    "Event was not routed to any queue: exchange={} routingKey={} reply={}",
                    returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
            template.setConfirmCallback((correlation, ack, cause) -> {
                if (!ack) {
                    log.error("Broker rejected an event (nack): {}", cause);
                }
            });
        };
    }
}
