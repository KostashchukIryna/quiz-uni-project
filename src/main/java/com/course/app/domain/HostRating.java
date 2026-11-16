package com.course.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "host_rating")
@IdClass(HostRatingId.class)
@Getter
@NoArgsConstructor
public class HostRating {

    @Id
    private String period;

    @Id
    private String username;

    @Column(name = "quizzes_total", nullable = false)
    private int quizzesTotal;

    @Column(name = "published_total", nullable = false)
    private int publishedTotal;

    @Column(name = "questions_total", nullable = false)
    private int questionsTotal;

    @Column(nullable = false)
    private int score;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    public HostRating(String period, String username, int quizzesTotal, int publishedTotal, int questionsTotal,
                      int score, Instant computedAt) {
        this.period = period;
        this.username = username;
        this.quizzesTotal = quizzesTotal;
        this.publishedTotal = publishedTotal;
        this.questionsTotal = questionsTotal;
        this.score = score;
        this.computedAt = computedAt;
    }
}
