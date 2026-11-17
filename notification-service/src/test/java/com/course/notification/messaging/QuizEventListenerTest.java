package com.course.notification.messaging;

import com.course.common.QuizEvent;
import com.course.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizEventListenerTest {

    @Mock
    NotificationService service;

    private static QuizEvent event() {
        return QuizEvent.of(QuizEvent.Type.QUIZ_CREATED, UUID.randomUUID(), "Java", "host1", Instant.now());
    }

    @Test
    void onEvent_handsTheEventToTheService() {
        QuizEvent event = event();
        when(service.handle(event)).thenReturn(true);

        new QuizEventListener(service).onEvent(event);

        verify(service).handle(event);
    }

    @Test
    void onEvent_duplicateEvent_isAcknowledgedWithoutError() {
        QuizEvent event = event();
        when(service.handle(event)).thenReturn(false);

        assertThatCode(() -> new QuizEventListener(service).onEvent(event)).doesNotThrowAnyException();
    }

    @Test
    void onEvent_raceOnTheUniqueEventId_isTreatedAsADuplicate() {
        QuizEvent event = event();
        when(service.handle(event)).thenThrow(new DataIntegrityViolationException("uq_notification_event_id"));

        assertThatCode(() -> new QuizEventListener(service).onEvent(event)).doesNotThrowAnyException();
    }

    @Test
    void onEvent_anyOtherFailure_propagates_soTheBrokerRetriesAndDeadLettersIt() {
        QuizEvent event = event();
        when(service.handle(event)).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> new QuizEventListener(service).onEvent(event))
                .isInstanceOf(IllegalStateException.class);
        assertThat(QuizEventListener.LISTENER_ID).isEqualTo("quizEvents");
    }
}
