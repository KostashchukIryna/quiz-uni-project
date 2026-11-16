package com.course.scheduler.service;

import com.course.common.QuizStatus;
import com.course.scheduler.domain.DraftReminder;
import com.course.scheduler.domain.QuizView;
import com.course.scheduler.repository.DraftReminderRepository;
import com.course.scheduler.repository.QuizViewRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaleDraftReminderServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Mock
    QuizViewRepository quizzes;
    @Mock
    DraftReminderRepository reminders;
    @Mock
    ReminderNotifier notifier;

    private StaleDraftReminderService service() {
        return new StaleDraftReminderService(quizzes, reminders, notifier, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static QuizView draft(String title, String host) {
        return new QuizView(UUID.randomUUID(), title, QuizStatus.DRAFT, host, NOW.minusSeconds(10 * 86_400));
    }

    @Test
    void remind_recordsEachStaleDraft_andNotifiesItsHost() {
        QuizView a = draft("Java", "host1");
        QuizView b = draft("Spring", "host2");
        when(quizzes.findStaleDrafts(any())).thenReturn(List.of(a, b));

        int sent = service().remind(7);

        assertThat(sent).isEqualTo(2);
        ArgumentCaptor<DraftReminder> saved = ArgumentCaptor.forClass(DraftReminder.class);
        verify(reminders, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(DraftReminder::getQuizId).containsExactly(a.getId(), b.getId());
        assertThat(saved.getAllValues()).extracting(DraftReminder::getRemindedAt).containsOnly(NOW);
        verify(notifier).remind("host1", "Java");
        verify(notifier).remind("host2", "Spring");
    }

    @Test
    void remind_looksOnlyForDraftsOlderThanTheCutoff() {
        when(quizzes.findStaleDrafts(any())).thenReturn(List.of());

        service().remind(7);

        verify(quizzes).findStaleDrafts(Instant.parse("2026-10-26T10:00:00Z"));
    }

    @Test
    void remind_withNothingStale_recordsAndSendsNothing() {
        when(quizzes.findStaleDrafts(any())).thenReturn(List.of());

        assertThat(service().remind(7)).isZero();

        verify(reminders, never()).save(any());
        verifyNoInteractions(notifier);
    }

    @Test
    void negativeThreshold_failsExplicitly_andTouchesNothing() {
        assertThatThrownBy(() -> service().remind(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("olderThanDays");

        verifyNoInteractions(quizzes, reminders, notifier);
    }
}
