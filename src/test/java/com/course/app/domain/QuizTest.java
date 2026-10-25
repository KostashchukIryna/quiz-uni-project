package com.course.app.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuizTest {

    @Test
    void setQuestions_newQuestions_linksEachQuestionBackToQuiz() {
        // Arrange
        Quiz quiz = new Quiz();
        Question q1 = new Question();
        Question q2 = new Question();

        // Act
        quiz.setQuestions(List.of(q1, q2));

        // Assert
        assertThat(quiz.getQuestions()).containsExactly(q1, q2);
        assertThat(q1.getQuiz()).isSameAs(quiz);
        assertThat(q2.getQuiz()).isSameAs(quiz);
    }

    @Test
    void setQuestions_calledTwice_replacesPreviousQuestions() {
        // Arrange
        Quiz quiz = new Quiz();
        quiz.setQuestions(new ArrayList<>(List.of(new Question())));
        Question replacement = new Question();

        // Act
        quiz.setQuestions(List.of(replacement));

        // Assert
        assertThat(quiz.getQuestions()).containsExactly(replacement);
    }

    @Test
    void setQuestions_null_clearsQuestionsWithoutFailing() {
        // Arrange
        Quiz quiz = new Quiz();
        quiz.setQuestions(List.of(new Question()));

        // Act
        quiz.setQuestions(null);

        // Assert
        assertThat(quiz.getQuestions()).isEmpty();
    }

    @Test
    void prePersist_emptyQuiz_generatesIdAndCreatedAt() {
        // Arrange
        Quiz quiz = new Quiz();

        // Act
        quiz.prePersist();

        // Assert
        assertThat(quiz.getId()).isNotNull();
        assertThat(quiz.getCreatedAt()).isNotNull();
    }

    @Test
    void prePersist_idAndCreatedAtAlreadySet_keepsThem() {
        // Arrange
        Quiz quiz = new Quiz();
        java.util.UUID id = java.util.UUID.randomUUID();
        java.time.Instant created = java.time.Instant.parse("2026-01-01T00:00:00Z");
        quiz.setId(id);
        quiz.setCreatedAt(created);

        // Act
        quiz.prePersist();

        // Assert
        assertThat(quiz.getId()).isEqualTo(id);
        assertThat(quiz.getCreatedAt()).isEqualTo(created);
    }

    @Test
    void newQuiz_defaultStatus_isDraft() {
        assertThat(new Quiz().getStatus()).isEqualTo(QuizStatus.DRAFT);
    }
}
