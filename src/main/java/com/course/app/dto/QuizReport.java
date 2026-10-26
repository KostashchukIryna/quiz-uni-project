package com.course.app.dto;

import java.util.UUID;

public record QuizReport(UUID quizId, String title, int questionCount,
                         boolean pdfAvailable, int pdfSizeBytes, QuizStats stats) {
}
