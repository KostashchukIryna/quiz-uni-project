package com.course.app.service;

import com.course.app.aop.Audited;
import com.course.app.async.QuizNotificationService;
import com.course.app.aop.RateLimited;
import com.course.app.aop.TranslateConflict;
import com.course.app.domain.Quiz;
import com.course.common.QuizEvent;
import com.course.common.QuizStatus;
import com.course.app.dto.QuizDto;
import com.course.app.mapper.QuizMapper;
import com.course.app.repository.QuizRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final QuizRepository repo;
    private final QuizMapper mapper;
    private final QuizNotificationService notifications;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<QuizDto> findAll() {
        return repo.findAll().stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public QuizDto findById(UUID id) {
        return mapper.toDto(get(id));
    }

    @PreAuthorize("hasAnyRole('HOST','ADMIN')")
    @RateLimited(maxCalls = 10, perSeconds = 60)
    @TranslateConflict
    @Audited(action = "CREATE_QUIZ")
    @Transactional
    public QuizDto create(QuizDto dto, String username) {
        Quiz quiz = mapper.toEntity(dto);
        quiz.setCreatedBy(username);
        QuizDto saved = mapper.toDto(repo.save(quiz));
        notifications.sendQuizCreatedEmail(username, saved.title()); // fire-and-forget, returns immediately
        publish(QuizEvent.Type.QUIZ_CREATED, saved.id(), saved.title(), username);
        return saved;
    }

    @PreAuthorize("hasAnyRole('HOST','ADMIN')")
    @TranslateConflict
    @Audited(action = "UPDATE_QUIZ")
    @Transactional
    public QuizDto update(UUID id, QuizDto dto) {
        Quiz quiz = get(id);
        QuizStatus before = quiz.getStatus();
        mapper.update(dto, quiz);
        if (before != QuizStatus.PUBLISHED && quiz.getStatus() == QuizStatus.PUBLISHED) {
            publish(QuizEvent.Type.QUIZ_PUBLISHED, quiz.getId(), quiz.getTitle(), quiz.getCreatedBy());
        }
        return mapper.toDto(quiz);
    }

    /**
     * Self-invocation demo (lab 4): this method is audited when called through the Spring proxy
     * (e.g. from the controller), but its call to {@code this.create(...)} bypasses the proxy, so
     * {@code create}'s @RateLimited / @TranslateConflict / @Audited do NOT run for the copy. Intentional.
     */
    @PreAuthorize("hasAnyRole('HOST','ADMIN')")
    @Audited(action = "DUPLICATE_QUIZ")
    @Transactional
    public QuizDto duplicate(UUID id, String username) {
        QuizDto source = findById(id);
        QuizDto copy = new QuizDto(null, source.title() + " (copy)", source.description(),
                QuizStatus.DRAFT, null, null, source.questions());
        return create(copy, username);
    }

    // The filter chain does not restrict DELETE by role - only this annotation does.
    @PreAuthorize("hasRole('ADMIN')")
    @Audited(action = "DELETE_QUIZ")
    @Transactional
    public void delete(UUID id) {
        Quiz quiz = get(id);
        repo.delete(quiz);
        publish(QuizEvent.Type.QUIZ_DELETED, quiz.getId(), quiz.getTitle(), quiz.getCreatedBy());
    }

    /**
     * Raised inside the transaction, delivered to the broker only after it commits
     * (see {@code QuizEventPublisher}).
     */
    private void publish(QuizEvent.Type type, UUID quizId, String title, String host) {
        events.publishEvent(QuizEvent.of(type, quizId, title, host, clock.instant()));
    }

    private Quiz get(UUID id) {
        return repo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found"));
    }
}
