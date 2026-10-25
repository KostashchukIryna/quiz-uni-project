package com.course.app.dto;

import com.course.app.domain.QuizStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuizDto(
        UUID id,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 1000) String description,
        QuizStatus status,
        String createdBy,
        Instant createdAt,
        @Valid List<QuestionDto> questions) {
}
