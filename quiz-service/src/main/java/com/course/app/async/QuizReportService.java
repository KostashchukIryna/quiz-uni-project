package com.course.app.async;

import com.course.app.dto.QuizDto;
import com.course.app.dto.QuizReport;
import com.course.app.dto.QuizStats;
import com.course.app.service.QuizService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Combines two independent slow calls (PDF + analytics) that run concurrently on the report executor.
 * <ul>
 *   <li>a failed call does not fail the request - it is replaced by a fallback ({@code exceptionally});</li>
 *   <li>waiting for the combined result is always bounded ({@code get(timeout)} -> 504);</li>
 *   <li>a full executor surfaces as TaskRejectedException, which the web layer maps to 503.</li>
 * </ul>
 */
@Service
public class QuizReportService {

    private static final Logger log = LoggerFactory.getLogger(QuizReportService.class);

    private final QuizService quizService;
    private final QuizPdfService pdfService;
    private final QuizStatsService statsService;
    private final Duration timeout;

    public QuizReportService(QuizService quizService, QuizPdfService pdfService, QuizStatsService statsService,
                             @Value("${app.async.report-timeout:3s}") Duration timeout) {
        this.quizService = quizService;
        this.pdfService = pdfService;
        this.statsService = statsService;
        this.timeout = timeout;
    }

    public QuizReport buildReport(UUID quizId) {
        QuizDto quiz = quizService.findById(quizId);

        // both calls are started before anything is awaited -> they run concurrently
        CompletableFuture<byte[]> pdf = pdfService.render(quiz)
                .exceptionally(ex -> {
                    log.warn("PDF generation failed for quiz {}: {}", quizId, ex.toString());
                    return null;
                });
        CompletableFuture<QuizStats> stats = statsService.fetch(quizId)
                .exceptionally(ex -> {
                    log.warn("Stats call failed for quiz {}: {}", quizId, ex.toString());
                    return QuizStats.unavailable();
                });

        CompletableFuture<QuizReport> combined = pdf.thenCombine(stats, (pdfBytes, quizStats) -> new QuizReport(
                quizId, quiz.title(), quiz.questions() == null ? 0 : quiz.questions().size(),
                pdfBytes != null, pdfBytes == null ? 0 : pdfBytes.length, quizStats));

        try {
            return combined.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            combined.cancel(true);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "Report is taking too long (limit " + timeout.toMillis() + " ms)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Report generation interrupted");
        } catch (ExecutionException e) {
            // unreachable in practice: both inputs have fallbacks, but never leak a raw failure
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Report generation failed", e.getCause());
        }
    }
}
