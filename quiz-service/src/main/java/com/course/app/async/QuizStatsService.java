package com.course.app.async;

import com.course.app.config.AsyncConfig;
import com.course.app.dto.QuizStats;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Slow side effect #3: call to an external analytics service (simulated, deterministic numbers). */
@Service
public class QuizStatsService {

    private final SlowWork slowWork;

    public QuizStatsService(SlowWork slowWork) {
        this.slowWork = slowWork;
    }

    @Async(AsyncConfig.REPORT_EXECUTOR)
    public CompletableFuture<QuizStats> fetch(UUID quizId) {
        slowWork.perform("stats");
        int plays = Math.floorMod(quizId.hashCode(), 100);
        return CompletableFuture.completedFuture(new QuizStats(true, plays, plays * 1.5));
    }
}
