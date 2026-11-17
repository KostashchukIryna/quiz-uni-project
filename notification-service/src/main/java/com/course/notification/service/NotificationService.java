package com.course.notification.service;

import com.course.common.QuizEvent;
import com.course.notification.domain.Notification;
import com.course.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Turns a quiz event into a stored notification for the quiz owner. Handling is idempotent: an event whose id was
 * already processed changes nothing, so a redelivered message is harmless.
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** @return true if the event was stored, false if it had been processed before */
    @Transactional
    public boolean handle(QuizEvent event) {
        if (repository.existsByEventId(event.eventId())) {
            return false;
        }
        repository.save(new Notification(event.eventId(), event.host(), event.type(), event.quizId(),
                messageFor(event), event.occurredAt(), clock.instant()));
        return true;
    }

    @Transactional(readOnly = true)
    public List<Notification> notificationsFor(String recipient) {
        return repository.findByRecipientOrderByOccurredAtDesc(recipient);
    }

    static String messageFor(QuizEvent event) {
        return switch (event.type()) {
            case QUIZ_CREATED -> "Your quiz '" + event.title() + "' was created";
            case QUIZ_PUBLISHED -> "Your quiz '" + event.title() + "' is now published";
            case QUIZ_DELETED -> "Your quiz '" + event.title() + "' was deleted";
        };
    }
}
