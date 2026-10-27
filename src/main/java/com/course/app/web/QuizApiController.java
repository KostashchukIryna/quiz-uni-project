package com.course.app.web;

import com.course.app.dto.QuizDto;
import com.course.app.service.QuizService;
import com.course.app.web.annotation.CurrentUsername;
import com.course.app.web.annotation.PostCreated;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class QuizApiController {

    private final QuizService service;

    @GetMapping
    public List<QuizDto> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public QuizDto get(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostCreated
    public QuizDto create(@Valid @RequestBody QuizDto dto, @CurrentUsername String username) {
        return service.create(dto, username);
    }

    @PostCreated("/{id}/duplicate")
    public QuizDto duplicate(@PathVariable UUID id, @CurrentUsername String username) {
        return service.duplicate(id, username);
    }

    @PutMapping("/{id}")
    public QuizDto update(@PathVariable UUID id, @Valid @RequestBody QuizDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
