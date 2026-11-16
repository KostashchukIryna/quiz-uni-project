package com.course.app.service;

import com.course.app.async.QuizNotificationService;
import com.course.app.domain.Quiz;
import com.course.app.domain.QuizStatus;
import com.course.app.repository.QuizRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaleDraftReminderServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Mock
    QuizRepository quizRepository;
    @Mock
    QuizNotificationService notifications;

    private StaleDraftReminderService service() {
        return new StaleDraftReminderService(quizRepository, notifications, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Quiz draft(String title, String host) {
        Quiz quiz = new Quiz();
        quiz.setTitle(title);
        quiz.setCreatedBy(host);
        return quiz;
    }

    @Test
    void remind_stampsEachStaleDraft_andNotifiesItsHost() {
        Quiz a = draft("Java", "host1");
        Quiz b = draft("Spring", "host2");
        when(quizRepository.findByStatusAndRemindedAtIsNullAndCreatedAtBefore(any(), any())).thenReturn(List.of(a, b));

        int sent = service().remind(7);

        assertThat(sent).isEqualTo(2);
        assertThat(a.getRemindedAt()).isEqualTo(NOW);
        assertThat(b.getRemindedAt()).isEqualTo(NOW);
        verify(notifications).sendDraftReminderEmail("host1", "Java");
        verify(notifications).sendDraftReminderEmail("host2", "Spring");
    }

    @Test
    void remind_looksOnlyForUnremindedDraftsOlderThanTheCutoff() {
        when(quizRepository.findByStatusAndRemindedAtIsNullAndCreatedAtBefore(any(), any())).thenReturn(List.of());

        service().remind(7);

        verify(quizRepository).findByStatusAndRemindedAtIsNullAndCreatedAtBefore(
                QuizStatus.DRAFT, Instant.parse("2026-10-26T10:00:00Z"));
    }

    @Test
    void remind_withNothingStale_sendsNothing() {
        when(quizRepository.findByStatusAndRemindedAtIsNullAndCreatedAtBefore(any(), any())).thenReturn(List.of());

        assertThat(service().remind(7)).isZero();

        verifyNoInteractions(notifications);
    }

    @Test
    void negativeThreshold_failsExplicitly_andTouchesNothing() {
        assertThatThrownBy(() -> service().remind(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("olderThanDays");

        verifyNoInteractions(quizRepository, notifications);
    }
}
