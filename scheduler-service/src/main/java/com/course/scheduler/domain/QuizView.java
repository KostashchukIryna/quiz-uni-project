package com.course.scheduler.domain;

import com.course.common.QuizStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only view of the {@code quiz} table. The table belongs to quiz-service (it creates and migrates it);
 * this service only reads the columns it needs and never writes to it.
 */
@Entity
@Immutable
@Table(name = "quiz")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizView {

    @Id
    private UUID id;

    private String title;

    @Enumerated(EnumType.STRING)
    private QuizStatus status;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    public QuizView(UUID id, String title, QuizStatus status, String createdBy, Instant createdAt) {
        this.id = id;
        this.title = title;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }
}
