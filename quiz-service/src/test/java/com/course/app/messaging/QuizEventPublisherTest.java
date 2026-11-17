package com.course.app.messaging;

import com.course.common.QuizEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QuizEventPublisherTest {

    @Mock
    RabbitTemplate rabbitTemplate;

    private static QuizEvent event(QuizEvent.Type type) {
        return QuizEvent.of(type, UUID.randomUUID(), "Java", "host1", Instant.parse("2026-11-17T10:00:00Z"));
    }

    @Test
    void publish_sendsTheEventToTheQuizExchangeWithTheRoutingKeyOfItsType() {
        QuizEvent created = event(QuizEvent.Type.QUIZ_CREATED);
        QuizEvent published = event(QuizEvent.Type.QUIZ_PUBLISHED);
        QuizEvent deleted = event(QuizEvent.Type.QUIZ_DELETED);
        QuizEventPublisher publisher = new QuizEventPublisher(rabbitTemplate);

        publisher.publish(created);
        publisher.publish(published);
        publisher.publish(deleted);

        verify(rabbitTemplate).convertAndSend("quiz.events", "quiz.created", created);
        verify(rabbitTemplate).convertAndSend("quiz.events", "quiz.published", published);
        verify(rabbitTemplate).convertAndSend("quiz.events", "quiz.deleted", deleted);
    }

    @Test
    void publish_brokerUnreachable_doesNotFailTheAlreadyCommittedRequest() {
        QuizEvent created = event(QuizEvent.Type.QUIZ_CREATED);
        doThrow(new AmqpConnectException(new IOException("connection refused")))
                .when(rabbitTemplate).convertAndSend("quiz.events", "quiz.created", created);

        assertThatCode(() -> new QuizEventPublisher(rabbitTemplate).publish(created)).doesNotThrowAnyException();
    }
}
