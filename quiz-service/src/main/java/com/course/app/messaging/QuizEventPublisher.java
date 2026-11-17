package com.course.app.messaging;

import com.course.common.QuizEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends a {@link QuizEvent} to the broker only after the transaction that raised it has committed. If the
 * transaction rolls back this listener never runs, so no event describes something that did not happen.
 *
 * <p>The send is the last step, after the commit. If the broker is unreachable at that moment the data is already
 * saved and the HTTP request must still succeed, so the failure is logged with the event id (the event can be
 * replayed from the log) and not propagated. Closing this gap completely needs a transactional outbox.
 */
@Component
public class QuizEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(QuizEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public QuizEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(QuizEvent event) {
        try {
            rabbitTemplate.convertAndSend(QuizEvent.EXCHANGE, event.routingKey(), event);
            log.info("Published {} for quiz {} (eventId={})", event.type(), event.quizId(), event.eventId());
        } catch (AmqpException e) {
            log.error("Could not publish {} for quiz {} (eventId={}); the quiz change is saved", event.type(),
                    event.quizId(), event.eventId(), e);
        }
    }
}
