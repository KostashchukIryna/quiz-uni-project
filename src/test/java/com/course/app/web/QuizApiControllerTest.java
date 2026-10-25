package com.course.app.web;

import com.course.app.domain.QuizStatus;
import com.course.app.dto.QuizDto;
import com.course.app.service.QuizService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizApiControllerTest {

    @Mock
    private QuizService service;
    @InjectMocks
    private QuizApiController controller;

    private static QuizDto dto(UUID id) {
        return new QuizDto(id, "t", "d", QuizStatus.DRAFT, "host1", null, List.of());
    }

    @Test
    void list_quizzesExist_returnsServiceResult() {
        // Arrange
        List<QuizDto> quizzes = List.of(dto(UUID.randomUUID()));
        when(service.findAll()).thenReturn(quizzes);

        // Act + Assert
        assertThat(controller.list()).isSameAs(quizzes);
    }

    @Test
    void get_missingQuiz_propagatesNotFound() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));

        // Act + Assert
        assertThatThrownBy(() -> controller.get(id)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void create_authenticatedHost_passesResolvedUsernameToService() {
        // Arrange
        QuizDto input = dto(null);
        QuizDto created = dto(UUID.randomUUID());
        when(service.create(input, "host1")).thenReturn(created);

        // Act
        QuizDto result = controller.create(input, "host1");

        // Assert
        verify(service).create(input, "host1");
        assertThat(result).isSameAs(created);
    }

    @Test
    void update_existingQuiz_delegatesWithIdAndBody() {
        // Arrange
        UUID id = UUID.randomUUID();
        QuizDto input = dto(id);
        when(service.update(id, input)).thenReturn(input);

        // Act
        QuizDto result = controller.update(id, input);

        // Assert
        assertThat(result).isSameAs(input);
        verify(service).update(id, input);
    }

    @Test
    void delete_anyId_delegatesToService() {
        // Arrange
        UUID id = UUID.randomUUID();

        // Act
        controller.delete(id);

        // Assert
        verify(service).delete(id);
    }
}
