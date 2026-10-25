package com.course.app.service;

import com.course.app.domain.Quiz;
import com.course.app.dto.QuizDto;
import com.course.app.mapper.QuizMapper;
import com.course.app.repository.QuizRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final QuizRepository repo;
    private final QuizMapper mapper;

    @Transactional(readOnly = true)
    public List<QuizDto> findAll() {
        return repo.findAll().stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public QuizDto findById(UUID id) {
        return mapper.toDto(get(id));
    }

    @PreAuthorize("hasAnyRole('HOST','ADMIN')")
    @Transactional
    public QuizDto create(QuizDto dto, String username) {
        Quiz quiz = mapper.toEntity(dto);
        quiz.setCreatedBy(username);
        return mapper.toDto(repo.save(quiz));
    }

    @PreAuthorize("hasAnyRole('HOST','ADMIN')")
    @Transactional
    public QuizDto update(UUID id, QuizDto dto) {
        Quiz quiz = get(id);
        mapper.update(dto, quiz);
        return mapper.toDto(quiz);
    }

    // The filter chain does not restrict DELETE by role - only this annotation does.
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete(UUID id) {
        repo.delete(get(id));
    }

    private Quiz get(UUID id) {
        return repo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found"));
    }
}
