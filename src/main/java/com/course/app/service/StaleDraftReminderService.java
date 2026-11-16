package com.course.app.service;

import com.course.app.async.QuizNotificationService;
import com.course.app.domain.Quiz;
import com.course.app.domain.QuizStatus;
import com.course.app.repository.QuizRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class StaleDraftReminderService {

    private final QuizRepository quizRepository;
    private final QuizNotificationService notifications;
    private final Clock clock;

    public StaleDraftReminderService(QuizRepository quizRepository, QuizNotificationService notifications, Clock clock) {
        this.quizRepository = quizRepository;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public int remind(int olderThanDays) {
        if (olderThanDays < 0) {
            throw new IllegalArgumentException("olderThanDays must not be negative: " + olderThanDays);
        }
        Instant now = clock.instant();
        List<Quiz> stale = quizRepository.findByStatusAndRemindedAtIsNullAndCreatedAtBefore(
                QuizStatus.DRAFT, now.minus(Duration.ofDays(olderThanDays)));
        for (Quiz quiz : stale) {
            quiz.setRemindedAt(now);
            notifications.sendDraftReminderEmail(quiz.getCreatedBy(), quiz.getTitle());
        }
        return stale.size();
    }
}
