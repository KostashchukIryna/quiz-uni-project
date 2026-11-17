package com.course.app.service;

import com.course.app.async.QuizNotificationService;
import com.course.app.domain.Quiz;
import com.course.common.QuizEvent;
import com.course.common.QuizStatus;
import com.course.app.dto.QuizDto;
import com.course.app.mapper.QuizMapper;
import com.course.app.repository.QuizRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock
    private QuizRepository repo;
    @Mock
    private QuizMapper mapper;
    @Mock
    private QuizNotificationService notifications;
    @Mock
    private ApplicationEventPublisher events;
    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
    @InjectMocks
    private QuizService service;

    private static QuizDto dto(UUID id, String title) {
        return new QuizDto(id, title, "desc", QuizStatus.DRAFT, null, null, List.of());
    }

    // ---------- findAll ----------

    @Test
    @DisplayName("findAll returns every quiz mapped to a DTO")
    void findAll_existingQuizzes_returnsMappedDtos() {
        // Arrange
        Quiz q1 = new Quiz();
        Quiz q2 = new Quiz();
        QuizDto d1 = dto(UUID.randomUUID(), "one");
        QuizDto d2 = dto(UUID.randomUUID(), "two");
        when(repo.findAll()).thenReturn(List.of(q1, q2));
        when(mapper.toDto(q1)).thenReturn(d1);
        when(mapper.toDto(q2)).thenReturn(d2);

        // Act
        List<QuizDto> result = service.findAll();

        // Assert
        assertThat(result).containsExactly(d1, d2);
    }

    @Test
    void findAll_noQuizzes_returnsEmptyListAndNeverMaps() {
        // Arrange
        when(repo.findAll()).thenReturn(List.of());

        // Act
        List<QuizDto> result = service.findAll();

        // Assert
        assertThat(result).isEmpty();
        verifyNoInteractions(mapper);
    }

    // ---------- findById ----------

    @Test
    void findById_existingQuiz_returnsMappedDto() {
        // Arrange
        UUID id = UUID.randomUUID();
        Quiz quiz = new Quiz();
        QuizDto expected = dto(id, "found");
        when(repo.findById(id)).thenReturn(Optional.of(quiz));
        when(mapper.toDto(quiz)).thenReturn(expected);

        // Act
        QuizDto result = service.findById(id);

        // Assert
        assertThat(result).isSameAs(expected);
    }

    @Test
    void findById_missingQuiz_throwsNotFound() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessageContaining("Quiz not found");
        verifyNoInteractions(mapper);
    }

    // ---------- create ----------

    @Test
    void create_validDto_setsCreatorSavesAndReturnsDto() {
        // Arrange
        QuizDto input = dto(null, "new quiz");
        QuizDto output = dto(UUID.randomUUID(), "new quiz");
        Quiz entity = new Quiz();
        when(mapper.toEntity(input)).thenReturn(entity);
        when(repo.save(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(output);

        // Act
        QuizDto result = service.create(input, "host1");

        // Assert
        ArgumentCaptor<Quiz> saved = ArgumentCaptor.forClass(Quiz.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getCreatedBy()).isEqualTo("host1");
        assertThat(result).isSameAs(output);
    }

    @Test
    void create_validDto_triggersFireAndForgetEmailWithUserAndTitle() {
        // Arrange
        QuizDto input = dto(null, "new quiz");
        QuizDto output = dto(UUID.randomUUID(), "new quiz");
        Quiz entity = new Quiz();
        when(mapper.toEntity(input)).thenReturn(entity);
        when(repo.save(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(output);

        // Act
        service.create(input, "host1");

        // Assert
        verify(notifications).sendQuizCreatedEmail("host1", "new quiz");
    }

    @Test
    void create_repositoryFails_propagatesExceptionAndReturnsNothing() {
        // Arrange
        QuizDto input = dto(null, "broken");
        Quiz entity = new Quiz();
        when(mapper.toEntity(input)).thenReturn(entity);
        when(repo.save(entity)).thenThrow(new IllegalStateException("db down"));

        // Act + Assert
        assertThatThrownBy(() -> service.create(input, "host1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
        verify(mapper, never()).toDto(any(Quiz.class));
        verifyNoInteractions(notifications);
    }

    // ---------- update ----------

    @Test
    void update_existingQuiz_appliesDtoToEntityAndReturnsDto() {
        // Arrange
        UUID id = UUID.randomUUID();
        Quiz quiz = new Quiz();
        QuizDto input = dto(id, "edited");
        QuizDto output = dto(id, "edited");
        when(repo.findById(id)).thenReturn(Optional.of(quiz));
        when(mapper.toDto(quiz)).thenReturn(output);

        // Act
        QuizDto result = service.update(id, input);

        // Assert
        verify(mapper).update(input, quiz);
        assertThat(result).isSameAs(output);
    }

    @Test
    void update_missingQuiz_throwsNotFoundAndDoesNotMap() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.update(id, dto(id, "x")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(mapper);
    }

    // ---------- delete ----------

    @Test
    void delete_existingQuiz_deletesThatEntity() {
        // Arrange
        UUID id = UUID.randomUUID();
        Quiz quiz = new Quiz();
        when(repo.findById(id)).thenReturn(Optional.of(quiz));

        // Act
        service.delete(id);

        // Assert
        ArgumentCaptor<Quiz> deleted = ArgumentCaptor.forClass(Quiz.class);
        verify(repo, times(1)).delete(deleted.capture());
        assertThat(deleted.getValue()).isSameAs(quiz);
    }

    @Test
    void delete_missingQuiz_throwsNotFoundAndDeletesNothing() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(repo, never()).delete(any(Quiz.class));
    }

    // ---------- events (lab 8) ----------

    private List<QuizEvent> publishedEvents() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events, atLeast(0)).publishEvent(captor.capture());
        return captor.getAllValues().stream().map(QuizEvent.class::cast).toList();
    }

    @Test
    void create_validDto_publishesExactlyOneQuizCreatedEvent() {
        // Arrange
        UUID id = UUID.randomUUID();
        QuizDto input = dto(null, "new quiz");
        QuizDto output = dto(id, "new quiz");
        Quiz entity = new Quiz();
        when(mapper.toEntity(input)).thenReturn(entity);
        when(repo.save(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(output);

        // Act
        service.create(input, "host1");

        // Assert
        assertThat(publishedEvents()).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(QuizEvent.Type.QUIZ_CREATED);
            assertThat(e.quizId()).isEqualTo(id);
            assertThat(e.title()).isEqualTo("new quiz");
            assertThat(e.host()).isEqualTo("host1");
            assertThat(e.occurredAt()).isEqualTo(Instant.parse("2026-11-17T10:00:00Z"));
            assertThat(e.eventId()).isNotNull();
        });
    }

    @Test
    void create_repositoryFails_publishesNoEvent() {
        // Arrange
        QuizDto input = dto(null, "broken");
        Quiz entity = new Quiz();
        when(mapper.toEntity(input)).thenReturn(entity);
        when(repo.save(entity)).thenThrow(new IllegalStateException("db down"));

        // Act
        assertThatThrownBy(() -> service.create(input, "host1")).isInstanceOf(IllegalStateException.class);

        // Assert
        verifyNoInteractions(events);
    }

    @Test
    void update_draftBecomesPublished_publishesQuizPublishedEventForTheOwner() {
        // Arrange
        UUID id = UUID.randomUUID();
        Quiz quiz = new Quiz();
        quiz.setTitle("Java");
        quiz.setCreatedBy("host1");
        quiz.setStatus(QuizStatus.DRAFT);
        QuizDto input = dto(id, "Java");
        when(repo.findById(id)).thenReturn(Optional.of(quiz));
        doAnswer(call -> {
            quiz.setStatus(QuizStatus.PUBLISHED);
            return null;
        }).when(mapper).update(input, quiz);

        // Act
        service.update(id, input);

        // Assert
        assertThat(publishedEvents()).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(QuizEvent.Type.QUIZ_PUBLISHED);
            assertThat(e.title()).isEqualTo("Java");
            assertThat(e.host()).isEqualTo("host1");
        });
    }

    @Test
    void update_statusUnchanged_publishesNoEvent() {
        // Arrange
        UUID id = UUID.randomUUID();
        Quiz quiz = new Quiz();
        quiz.setStatus(QuizStatus.DRAFT);
        when(repo.findById(id)).thenReturn(Optional.of(quiz));

        // Act
        service.update(id, dto(id, "only the title changed"));

        // Assert
        verifyNoInteractions(events);
    }

    @Test
    void update_alreadyPublishedQuiz_publishesNoSecondPublishedEvent() {
        // Arrange
        UUID id = UUID.randomUUID();
        Quiz quiz = new Quiz();
        quiz.setStatus(QuizStatus.PUBLISHED);
        when(repo.findById(id)).thenReturn(Optional.of(quiz));

        // Act
        service.update(id, dto(id, "edited again"));

        // Assert
        verifyNoInteractions(events);
    }

    @Test
    void delete_existingQuiz_publishesQuizDeletedEventForTheOwner() {
        // Arrange
        UUID id = UUID.randomUUID();
        Quiz quiz = new Quiz();
        quiz.setTitle("Old quiz");
        quiz.setCreatedBy("host2");
        when(repo.findById(id)).thenReturn(Optional.of(quiz));

        // Act
        service.delete(id);

        // Assert
        assertThat(publishedEvents()).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(QuizEvent.Type.QUIZ_DELETED);
            assertThat(e.title()).isEqualTo("Old quiz");
            assertThat(e.host()).isEqualTo("host2");
        });
    }

    @Test
    void delete_missingQuiz_publishesNoEvent() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        // Act
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ResponseStatusException.class);

        // Assert
        verifyNoInteractions(events);
    }
}
