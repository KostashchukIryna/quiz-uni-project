package com.course.app.web;

import com.course.app.domain.QuizStatus;
import com.course.app.dto.QuizDto;
import com.course.app.service.QuizService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizUiControllerTest {

    @Mock
    private QuizService service;
    @Mock
    private OidcUser user;
    @InjectMocks
    private QuizUiController controller;

    @Test
    void list_quizzesExist_putsThemIntoModelAndReturnsView() {
        // Arrange
        List<QuizDto> quizzes = List.of(new QuizDto(UUID.randomUUID(), "t", null, QuizStatus.DRAFT, "h", null, List.of()));
        when(service.findAll()).thenReturn(quizzes);
        Model model = new ConcurrentModel();

        // Act
        String view = controller.list(model);

        // Assert
        assertThat(view).isEqualTo("quizzes");
        assertThat(model.getAttribute("quizzes")).isEqualTo(quizzes);
    }

    @Test
    void create_titleAndDescription_createsDraftQuizForCurrentUser() {
        // Arrange
        when(user.getPreferredUsername()).thenReturn("host1");

        // Act
        String view = controller.create("My quiz", "about", user);

        // Assert
        ArgumentCaptor<QuizDto> dto = ArgumentCaptor.forClass(QuizDto.class);
        verify(service).create(dto.capture(), eq("host1"));
        assertThat(dto.getValue().title()).isEqualTo("My quiz");
        assertThat(dto.getValue().description()).isEqualTo("about");
        assertThat(dto.getValue().status()).isEqualTo(QuizStatus.DRAFT);
        assertThat(view).isEqualTo("redirect:/ui/quizzes");
    }

    @Test
    void update_existingQuiz_keepsStatusAndQuestionsAndChangesTitle() {
        // Arrange
        UUID id = UUID.randomUUID();
        QuizDto old = new QuizDto(id, "old", "d", QuizStatus.PUBLISHED, "h", null, List.of());
        when(service.findById(id)).thenReturn(old);

        // Act
        String view = controller.update(id, "new", "nd");

        // Assert
        ArgumentCaptor<QuizDto> dto = ArgumentCaptor.forClass(QuizDto.class);
        verify(service).update(eq(id), dto.capture());
        assertThat(dto.getValue().title()).isEqualTo("new");
        assertThat(dto.getValue().status()).isEqualTo(QuizStatus.PUBLISHED);
        assertThat(dto.getValue().questions()).isSameAs(old.questions());
        assertThat(view).isEqualTo("redirect:/ui/quizzes");
    }

    @Test
    void delete_anyId_delegatesAndRedirects() {
        // Arrange
        UUID id = UUID.randomUUID();

        // Act
        String view = controller.delete(id);

        // Assert
        verify(service).delete(id);
        assertThat(view).isEqualTo("redirect:/ui/quizzes");
    }
}
