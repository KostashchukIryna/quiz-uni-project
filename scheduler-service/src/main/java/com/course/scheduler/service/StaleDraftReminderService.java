package com.course.scheduler.service;

import com.course.scheduler.domain.DraftReminder;
import com.course.scheduler.domain.QuizView;
import com.course.scheduler.repository.DraftReminderRepository;
import com.course.scheduler.repository.QuizViewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Reminds hosts about drafts that have been waiting too long. Each reminded draft is recorded in
 * {@code draft_reminder} in the same transaction, so a second run right after the first finds nothing to do:
 * the job is idempotent. The quiz table itself is never written, it belongs to quiz-service.
 */
@Service
public class StaleDraftReminderService {

    private final QuizViewRepository quizzes;
    private final DraftReminderRepository reminders;
    private final ReminderNotifier notifier;
    private final Clock clock;

    public StaleDraftReminderService(QuizViewRepository quizzes, DraftReminderRepository reminders,
                                     ReminderNotifier notifier, Clock clock) {
        this.quizzes = quizzes;
        this.reminders = reminders;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** @return number of reminders sent */
    @Transactional
    public int remind(int olderThanDays) {
        if (olderThanDays < 0) {
            throw new IllegalArgumentException("olderThanDays must not be negative: " + olderThanDays);
        }
        Instant now = clock.instant();
        List<QuizView> stale = quizzes.findStaleDrafts(now.minus(Duration.ofDays(olderThanDays)));
        for (QuizView quiz : stale) {
            reminders.save(new DraftReminder(quiz.getId(), now));
            notifier.remind(quiz.getCreatedBy(), quiz.getTitle());
        }
        return stale.size();
    }
}
