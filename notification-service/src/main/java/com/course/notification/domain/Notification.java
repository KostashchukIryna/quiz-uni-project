package com.course.notification.domain;

import com.course.common.QuizEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** What a host is told about one event. {@code eventId} is unique, so an event is stored at most once. */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(nullable = false)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuizEvent.Type type;

    @Column(name = "quiz_id", nullable = false)
    private UUID quizId;

    @Column(nullable = false)
    private String message;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    public Notification(UUID eventId, String recipient, QuizEvent.Type type, UUID quizId, String message,
                        Instant occurredAt, Instant receivedAt) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.recipient = recipient;
        this.type = type;
        this.quizId = quizId;
        this.message = message;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
    }
}
