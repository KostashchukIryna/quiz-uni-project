package com.course.app.service;

import com.course.app.repository.QuizRepository;
import com.course.app.repository.QuizRepository.HostActivity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import com.course.app.domain.HostRating;
import com.course.app.repository.HostRatingRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HostRatingServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Mock
    QuizRepository quizRepository;
    @Mock
    HostRatingRepository ratingRepository;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private HostRatingService service() {
        return new HostRatingService(quizRepository, ratingRepository, clock);
    }

    private static HostActivity activity(String username, long quizzes, long published, long questions) {
        return new HostActivity() {
            public String getUsername() { return username; }
            public long getQuizzes() { return quizzes; }
            public long getPublished() { return published; }
            public long getQuestions() { return questions; }
        };
    }

    @SuppressWarnings("unchecked")
    private List<HostRating> savedRatings() {
        ArgumentCaptor<List<HostRating>> captor = ArgumentCaptor.forClass(List.class);
        verify(ratingRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void score_isQuizzesPlusTwicePublishedPlusQuestions() {
        assertThat(HostRatingService.score(3, 2, 10)).isEqualTo(3 + 4 + 10);
        assertThat(HostRatingService.score(0, 0, 0)).isZero();
    }

    @Test
    void recalculate_writesOneRatingPerHost_withTheComputedScore() {
        when(quizRepository.summarizeByHost(any())).thenReturn(List.of(activity("host1", 3, 2, 10)));

        int written = service().recalculate("ALL_TIME", 0);

        assertThat(written).isEqualTo(1);
        HostRating rating = savedRatings().get(0);
        assertThat(rating.getPeriod()).isEqualTo("ALL_TIME");
        assertThat(rating.getUsername()).isEqualTo("host1");
        assertThat(rating.getQuizzesTotal()).isEqualTo(3);
        assertThat(rating.getPublishedTotal()).isEqualTo(2);
        assertThat(rating.getQuestionsTotal()).isEqualTo(10);
        assertThat(rating.getScore()).isEqualTo(17);
        assertThat(rating.getComputedAt()).isEqualTo(NOW);
    }

    @Test
    void recalculate_deletesThePeriodBeforeInserting_soTheSnapshotIsComplete() {
        when(quizRepository.summarizeByHost(any())).thenReturn(List.of(activity("host1", 1, 0, 1)));

        service().recalculate("ALL_TIME", 0);

        InOrder order = inOrder(ratingRepository);
        order.verify(ratingRepository).deleteByPeriod("ALL_TIME");
        order.verify(ratingRepository).saveAll(any());
    }

    @Test
    void recalculate_withNoActivity_clearsTheOldRatings_andWritesNothing() {
        when(quizRepository.summarizeByHost(any())).thenReturn(List.of());

        int written = service().recalculate("ALL_TIME", 0);

        assertThat(written).isZero();
        verify(ratingRepository).deleteByPeriod("ALL_TIME");
        assertThat(savedRatings()).isEmpty();
    }

    @Test
    void zeroWindow_meansNoLimit() {
        when(quizRepository.summarizeByHost(any())).thenReturn(List.of());

        service().recalculate("ALL_TIME", 0);

        verify(quizRepository).summarizeByHost(Instant.EPOCH);
    }

    @Test
    void window_limitsTheQuizzesToTheLastNDays() {
        when(quizRepository.summarizeByHost(any())).thenReturn(List.of());

        service().recalculate("LAST_7_DAYS", 7);

        verify(quizRepository).summarizeByHost(Instant.parse("2026-10-26T10:00:00Z"));
    }

    @Test
    void runningTwice_onTheSameData_producesTheSameRatings() {
        when(quizRepository.summarizeByHost(any())).thenReturn(List.of(
                activity("host1", 3, 2, 10), activity("host2", 1, 0, 2)));
        HostRatingService service = service();

        service.recalculate("ALL_TIME", 0);
        service.recalculate("ALL_TIME", 0);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HostRating>> captor = ArgumentCaptor.forClass(List.class);
        verify(ratingRepository, times(2)).saveAll(captor.capture());
        assertThat(captor.getAllValues().get(1))
                .usingRecursiveComparison()
                .isEqualTo(captor.getAllValues().get(0));
    }

    @Test
    void negativeWindow_failsExplicitly_andTouchesNothing() {
        assertThatThrownBy(() -> service().recalculate("ALL_TIME", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("windowDays");

        verifyNoInteractions(quizRepository, ratingRepository);
    }

    @Test
    void blankPeriod_failsExplicitly() {
        assertThatThrownBy(() -> service().recalculate(" ", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("period");
        assertThatThrownBy(() -> service().recalculate(null, 0))
                .isInstanceOf(IllegalArgumentException.class);

        verify(ratingRepository, never()).deleteByPeriod(any());
    }

    @Test
    void leaderboard_delegatesToTheRepository() {
        List<HostRating> rows = List.of(new HostRating("ALL_TIME", "host1", 1, 1, 1, 4, NOW));
        when(ratingRepository.findByPeriodOrderByScoreDescUsernameAsc("ALL_TIME")).thenReturn(rows);

        assertThat(service().leaderboard("ALL_TIME")).isSameAs(rows);
    }
}
