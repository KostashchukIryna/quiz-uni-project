package com.course.scheduler.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

/** Read-only view of the {@code question} table (owned by quiz-service); only used to count questions per host. */
@Entity
@Immutable
@Table(name = "question")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionView {

    @Id
    private UUID id;

    @Column(name = "quiz_id")
    private UUID quizId;
}
