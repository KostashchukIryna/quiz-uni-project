package com.course.scheduler.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Remembers that the host of a draft was already reminded, so the reminder job never repeats itself. */
@Entity
@Table(name = "draft_reminder")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DraftReminder {

    @Id
    @Column(name = "quiz_id")
    private UUID quizId;

    @Column(name = "reminded_at", nullable = false)
    private Instant remindedAt;

    public DraftReminder(UUID quizId, Instant remindedAt) {
        this.quizId = quizId;
        this.remindedAt = remindedAt;
    }
}
