package com.course.app.web;

import com.course.app.dto.QuizDto;
import com.course.app.service.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuizDto create(@Valid @RequestBody QuizDto dto, @AuthenticationPrincipal Jwt jwt) {
        return service.create(dto, jwt.getClaimAsString("preferred_username"));
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
