package com.course.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Events arrive as JSON and are turned into the shared {@code QuizEvent}. The type is taken from the listener
 * method parameter, so nothing about the producer's classes is needed. The queue and its dead-letter queue are
 * provisioned by the broker (see rabbitmq/definitions.json).
 */
@Configuration
public class MessagingConfig {

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
