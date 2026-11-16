package com.course.app.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionTest {

    @Test
    void prePersist_noId_generatesId() {
        // Arrange
        Question question = new Question();

        // Act
        question.prePersist();

        // Assert
        assertThat(question.getId()).isNotNull();
    }

    @Test
    void prePersist_existingId_keepsId() {
        // Arrange
        Question question = new Question();
        UUID id = UUID.randomUUID();
        question.setId(id);

        // Act
        question.prePersist();

        // Assert
        assertThat(question.getId()).isEqualTo(id);
    }
}
