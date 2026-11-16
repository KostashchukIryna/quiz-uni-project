package com.course.app.service;

import com.course.app.repository.QuizRepository;
import com.course.app.repository.QuizRepository.HostActivity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.course.app.domain.HostRating;
import com.course.app.repository.HostRatingRepository;

@Service
public class HostRatingService {

    private final QuizRepository quizRepository;
    private final HostRatingRepository ratingRepository;
    private final Clock clock;

    public HostRatingService(QuizRepository quizRepository, HostRatingRepository ratingRepository, Clock clock) {
        this.quizRepository = quizRepository;
        this.ratingRepository = ratingRepository;
        this.clock = clock;
    }

    static int score(long quizzes, long published, long questions) {
        return Math.toIntExact(quizzes + 2 * published + questions);
    }

    @Transactional
    public int recalculate(String period, int windowDays) {
        if (period == null || period.isBlank()) {
            throw new IllegalArgumentException("period must not be blank");
        }
        if (windowDays < 0) {
            throw new IllegalArgumentException("windowDays must not be negative: " + windowDays);
        }
        Instant now = clock.instant();
        Instant since = windowDays == 0 ? Instant.EPOCH : now.minus(Duration.ofDays(windowDays));

        List<HostRating> ratings = quizRepository.summarizeByHost(since).stream()
                .map(a -> toRating(period, a, now))
                .toList();

        ratingRepository.deleteByPeriod(period);
        ratingRepository.saveAll(ratings);
        return ratings.size();
    }

    private static HostRating toRating(String period, HostActivity a, Instant now) {
        return new HostRating(period, a.getUsername(), (int) a.getQuizzes(), (int) a.getPublished(),
                (int) a.getQuestions(), score(a.getQuizzes(), a.getPublished(), a.getQuestions()), now);
    }

    @Transactional(readOnly = true)
    public List<HostRating> leaderboard(String period) {
        return ratingRepository.findByPeriodOrderByScoreDescUsernameAsc(period);
    }
}
