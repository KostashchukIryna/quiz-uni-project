package com.course.app.async;

import com.course.app.config.AsyncConfig;
import com.course.app.dto.QuizDto;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/** Slow side effect #2: PDF generation (content is a stand-in for a real PDF engine). */
@Service
public class QuizPdfService {

    private final SlowWork slowWork;

    public QuizPdfService(SlowWork slowWork) {
        this.slowWork = slowWork;
    }

    @Async(AsyncConfig.REPORT_EXECUTOR)
    public CompletableFuture<byte[]> render(QuizDto quiz) {
        slowWork.perform("pdf");
        byte[] pdf = ("%PDF-1.4 quiz: " + quiz.title()).getBytes(StandardCharsets.UTF_8);
        return CompletableFuture.completedFuture(pdf);
    }
}
