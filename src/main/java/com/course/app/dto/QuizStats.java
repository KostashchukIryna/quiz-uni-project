package com.course.app.dto;

public record QuizStats(boolean available, int plays, double averageScore) {

    public static QuizStats unavailable() {
        return new QuizStats(false, 0, 0);
    }
}
