package com.course.notification.messaging;

import com.course.common.QuizEvent;
import com.course.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Receives quiz events from the broker. The message is acknowledged when this method returns; if it throws, the
 * listener retries and finally rejects the message to the dead-letter queue (nothing is silently lost).
 */
@Component
public class QuizEventListener {

    public static final String LISTENER_ID = "quizEvents";

    private static final Logger log = LoggerFactory.getLogger(QuizEventListener.class);

    private final NotificationService service;

    public QuizEventListener(NotificationService service) {
        this.service = service;
    }

    @RabbitListener(id = LISTENER_ID, queues = "${app.messaging.queue}")
    public void onEvent(QuizEvent event) {
        try {
            if (service.handle(event)) {
                log.info("Stored notification for {} (eventId={})", event.type(), event.eventId());
            } else {
                log.info("Event {} was already processed, ignored", event.eventId());
            }
        } catch (DataIntegrityViolationException duplicate) {
            // two deliveries of the same event raced each other; the unique event id kept only one record
            log.info("Event {} was stored by a concurrent delivery, ignored", event.eventId());
        }
    }
}
