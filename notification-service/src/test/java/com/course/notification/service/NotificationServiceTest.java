package com.course.notification.service;

import com.course.common.QuizEvent;
import com.course.notification.domain.Notification;
import com.course.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant OCCURRED = Instant.parse("2026-11-17T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-17T10:00:05Z");

    @Mock
    NotificationRepository repository;

    private NotificationService service() {
        return new NotificationService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static QuizEvent event(QuizEvent.Type type) {
        return QuizEvent.of(type, UUID.randomUUID(), "Java", "host1", OCCURRED);
    }

    private Notification savedNotification() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void newEvent_isStoredForTheQuizOwner() {
        QuizEvent event = event(QuizEvent.Type.QUIZ_CREATED);
        when(repository.existsByEventId(event.eventId())).thenReturn(false);

        boolean stored = service().handle(event);

        assertThat(stored).isTrue();
        Notification saved = savedNotification();
        assertThat(saved.getEventId()).isEqualTo(event.eventId());
        assertThat(saved.getRecipient()).isEqualTo("host1");
        assertThat(saved.getType()).isEqualTo(QuizEvent.Type.QUIZ_CREATED);
        assertThat(saved.getQuizId()).isEqualTo(event.quizId());
        assertThat(saved.getMessage()).isEqualTo("Your quiz 'Java' was created");
        assertThat(saved.getOccurredAt()).isEqualTo(OCCURRED);
        assertThat(saved.getReceivedAt()).isEqualTo(NOW);
    }

    @Test
    void sameEventDeliveredTwice_isStoredOnce_andTheSecondDeliveryChangesNothing() {
        QuizEvent event = event(QuizEvent.Type.QUIZ_PUBLISHED);
        when(repository.existsByEventId(event.eventId())).thenReturn(false, true);
        NotificationService service = service();

        boolean first = service.handle(event);
        boolean second = service.handle(event);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        verify(repository, times(1)).save(any(Notification.class));
    }

    @Test
    void alreadyProcessedEvent_isNotSavedAgain() {
        QuizEvent event = event(QuizEvent.Type.QUIZ_DELETED);
        when(repository.existsByEventId(event.eventId())).thenReturn(true);

        assertThat(service().handle(event)).isFalse();

        verify(repository, never()).save(any(Notification.class));
    }

    @Test
    void differentEvents_areEachStored() {
        QuizEvent a = event(QuizEvent.Type.QUIZ_CREATED);
        QuizEvent b = event(QuizEvent.Type.QUIZ_CREATED);
        when(repository.existsByEventId(any(UUID.class))).thenReturn(false);

        service().handle(a);
        service().handle(b);

        verify(repository, times(2)).save(any(Notification.class));
    }

    @Test
    void messageDependsOnTheEventType() {
        assertThat(NotificationService.messageFor(event(QuizEvent.Type.QUIZ_CREATED)))
                .isEqualTo("Your quiz 'Java' was created");
        assertThat(NotificationService.messageFor(event(QuizEvent.Type.QUIZ_PUBLISHED)))
                .isEqualTo("Your quiz 'Java' is now published");
        assertThat(NotificationService.messageFor(event(QuizEvent.Type.QUIZ_DELETED)))
                .isEqualTo("Your quiz 'Java' was deleted");
    }

    @Test
    void notificationsFor_returnsTheRepositoryListOfThatRecipient() {
        List<Notification> list = List.of();
        when(repository.findByRecipientOrderByOccurredAtDesc("host1")).thenReturn(list);

        assertThat(service().notificationsFor("host1")).isSameAs(list);
    }
}
