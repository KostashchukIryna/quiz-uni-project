package com.course.notification.web;

import com.course.common.QuizEvent;
import com.course.notification.domain.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, QuizEvent.Type type, UUID quizId, String message, Instant occurredAt) {

    static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getQuizId(), n.getMessage(), n.getOccurredAt());
    }
}
