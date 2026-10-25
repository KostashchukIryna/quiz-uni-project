package com.course.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record QuestionDto(
        UUID id,
        @NotBlank @Size(max = 500) String text,
        @Min(5) @Max(120) int timeLimitSeconds,
        @Min(0) int points,
        @Min(0) int correctOption,
        @Size(min = 2, max = 6) List<@NotBlank String> options) {
}
