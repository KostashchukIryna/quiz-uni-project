package com.course.app.async;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.course.app.config.AsyncConfig;
import com.course.app.domain.QuizStatus;
import com.course.app.dto.QuizDto;
import com.course.app.dto.QuizReport;
import com.course.app.service.QuizService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Real @Async proxies and the real AsyncConfig executors in a minimal Spring context (no Boot, no DB).
 * Timing is controlled with latches/barriers - there is no Thread.sleep in the tests.
 */
class AsyncProcessingTest {

    /** Test double for the slow part: records which thread ran each operation and runs a scripted behaviour. */
    static class ControllableSlowWork implements SlowWork {
        final Map<String, String> threadByOperation = new ConcurrentHashMap<>();
        volatile Consumer<String> behaviour = op -> {
        };

        @Override
        public void perform(String operation) {
            threadByOperation.put(operation, Thread.currentThread().getName());
            behaviour.accept(operation);
        }
    }

    private static final UUID QUIZ_ID = UUID.fromString("00000000-0000-0000-0000-00000000002a");
    private static final QuizDto QUIZ = new QuizDto(QUIZ_ID, "Java Basics", "d", QuizStatus.DRAFT, "host1", null, List.of());
    private static final long WAIT_SECONDS = 5;

    private final ControllableSlowWork work = new ControllableSlowWork();
    private final QuizService quizService = mock(QuizService.class);
    private final List<CountDownLatch> latchesToRelease = new ArrayList<>();
    private final List<AnnotationConfigApplicationContext> contexts = new ArrayList<>();

    @BeforeEach
    void stubQuizLookup() {
        when(quizService.findById(QUIZ_ID)).thenReturn(QUIZ);
    }

    @AfterEach
    void shutDown() {
        latchesToRelease.forEach(CountDownLatch::countDown); // never leave a worker thread blocked
        contexts.forEach(AnnotationConfigApplicationContext::close);
    }

    private CountDownLatch latch() {
        CountDownLatch l = new CountDownLatch(1);
        latchesToRelease.add(l);
        return l;
    }

    private AnnotationConfigApplicationContext context(Duration reportTimeout,
                                                       ThreadPoolTaskExecutor reportExecutorOverride,
                                                       ThreadPoolTaskExecutor notificationExecutorOverride) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(AsyncConfig.class);
        if (reportExecutorOverride != null) {
            ctx.registerBean(AsyncConfig.REPORT_EXECUTOR, ThreadPoolTaskExecutor.class, () -> reportExecutorOverride);
        }
        if (notificationExecutorOverride != null) {
            ctx.registerBean(AsyncConfig.NOTIFICATION_EXECUTOR, ThreadPoolTaskExecutor.class, () -> notificationExecutorOverride);
        }
        ctx.registerBean(SlowWork.class, () -> work);
        ctx.registerBean(QuizPdfService.class);
        ctx.registerBean(QuizStatsService.class);
        ctx.registerBean(QuizNotificationService.class);
        ctx.registerBean(QuizReportService.class, () -> new QuizReportService(quizService,
                ctx.getBean(QuizPdfService.class), ctx.getBean(QuizStatsService.class), reportTimeout));
        ctx.refresh();
        contexts.add(ctx);
        return ctx;
    }

    private AnnotationConfigApplicationContext context() {
        return context(Duration.ofSeconds(WAIT_SECONDS), null, null);
    }

    private static ThreadPoolTaskExecutor tinyExecutor(String prefix, java.util.concurrent.RejectedExecutionHandler handler) {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(1);
        e.setMaxPoolSize(1);
        e.setQueueCapacity(0);
        e.setThreadNamePrefix(prefix);
        e.setRejectedExecutionHandler(handler);
        return e;
    }

    // ---------- explicit, bounded executors ----------

    @Test
    void executors_areExplicitlyBoundedAndNamed() {
        // Arrange + Act
        AnnotationConfigApplicationContext ctx = context();
        ThreadPoolTaskExecutor report = ctx.getBean(AsyncConfig.REPORT_EXECUTOR, ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor notify = ctx.getBean(AsyncConfig.NOTIFICATION_EXECUTOR, ThreadPoolTaskExecutor.class);

        // Assert
        assertThat(report.getCorePoolSize()).isEqualTo(2);
        assertThat(report.getMaxPoolSize()).isEqualTo(4);
        assertThat(report.getQueueCapacity()).isEqualTo(10);
        assertThat(report.getThreadNamePrefix()).isEqualTo("report-");
        assertThat(report.getThreadPoolExecutor().getRejectedExecutionHandler())
                .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        assertThat(notify.getCorePoolSize()).isEqualTo(1);
        assertThat(notify.getMaxPoolSize()).isEqualTo(2);
        assertThat(notify.getQueueCapacity()).isEqualTo(50);
        assertThat(notify.getThreadNamePrefix()).isEqualTo("notify-");
        assertThat(notify.getThreadPoolExecutor().getRejectedExecutionHandler())
                .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
    }

    // ---------- the slow method no longer blocks the caller ----------

    @Test
    void render_slowWorkStillRunning_callReturnsImmediatelyAndFinishesOnReportThread() throws Exception {
        // Arrange
        CountDownLatch started = latch();
        CountDownLatch release = latch();
        work.behaviour = op -> {
            started.countDown();
            blockUntil(release);
        };
        QuizPdfService pdf = context().getBean(QuizPdfService.class);

        // Act
        Future<byte[]> future = pdf.render(QUIZ);

        // Assert: control came back while the slow method has not finished
        assertThat(started.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(future.isDone()).isFalse();
        release.countDown();
        assertThat(future.get(WAIT_SECONDS, TimeUnit.SECONDS)).isNotEmpty();
        assertThat(work.threadByOperation.get("pdf"))
                .startsWith("report-")
                .isNotEqualTo(Thread.currentThread().getName())
                .doesNotStartWith("SimpleAsyncTaskExecutor")
                .doesNotStartWith("task-");
    }

    @Test
    void sendEmail_slowWorkStillRunning_callReturnsImmediatelyAndRunsOnNotifyThread() throws Exception {
        // Arrange
        CountDownLatch started = latch();
        CountDownLatch release = latch();
        work.behaviour = op -> {
            started.countDown();
            blockUntil(release);
        };
        QuizNotificationService notifications = context().getBean(QuizNotificationService.class);

        // Act
        notifications.sendQuizCreatedEmail("host1", "Java Basics");

        // Assert
        assertThat(started.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(release.getCount()).isEqualTo(1); // the caller got control back while the email is "in flight"
        assertThat(work.threadByOperation.get("email")).startsWith("notify-");
    }

    // ---------- two independent calls run at the same time ----------

    @Test
    void buildReport_pdfAndStatsAreIndependent_runConcurrentlyAndAreCombined() {
        // Arrange: both operations meet at a barrier - possible only if they execute at the same time
        CyclicBarrier bothRunning = new CyclicBarrier(2);
        work.behaviour = op -> {
            try {
                bothRunning.await(WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                throw new IllegalStateException("operations did not run concurrently: " + op, e);
            }
        };
        QuizReportService reports = context().getBean(QuizReportService.class);

        // Act
        QuizReport report = reports.buildReport(QUIZ_ID);

        // Assert
        assertThat(report.pdfAvailable()).isTrue();
        assertThat(report.stats().available()).isTrue();
        assertThat(report.title()).isEqualTo("Java Basics");
        assertThat(work.threadByOperation.get("pdf")).startsWith("report-");
        assertThat(work.threadByOperation.get("stats")).startsWith("report-");
        assertThat(work.threadByOperation.get("pdf")).isNotEqualTo(work.threadByOperation.get("stats"));
    }

    // ---------- failures never break the caller ----------

    @Test
    void buildReport_statsCallFails_returnsReportWithFallbackStats() {
        // Arrange
        work.behaviour = op -> {
            if (op.equals("stats")) {
                throw new IllegalStateException("analytics down");
            }
        };

        // Act
        QuizReport report = context().getBean(QuizReportService.class).buildReport(QUIZ_ID);

        // Assert
        assertThat(report.pdfAvailable()).isTrue();
        assertThat(report.stats().available()).isFalse();
    }

    @Test
    void buildReport_pdfFails_returnsReportWithoutPdf() {
        // Arrange
        work.behaviour = op -> {
            if (op.equals("pdf")) {
                throw new IllegalStateException("renderer crashed");
            }
        };

        // Act
        QuizReport report = context().getBean(QuizReportService.class).buildReport(QUIZ_ID);

        // Assert
        assertThat(report.pdfAvailable()).isFalse();
        assertThat(report.pdfSizeBytes()).isZero();
        assertThat(report.stats().available()).isTrue();
    }

    @Test
    void buildReport_bothCallsFail_stillReturnsDegradedReport() {
        // Arrange
        work.behaviour = op -> {
            throw new IllegalStateException("everything is down");
        };

        // Act
        QuizReport report = context().getBean(QuizReportService.class).buildReport(QUIZ_ID);

        // Assert
        assertThat(report.pdfAvailable()).isFalse();
        assertThat(report.stats().available()).isFalse();
        assertThat(report.quizId()).isEqualTo(QUIZ_ID);
    }

    @Test
    void sendEmail_voidAsyncMethodThrows_callerIsUnaffectedAndHandlerLogsTheError() {
        // Arrange
        Logger handlerLogger = (Logger) LoggerFactory.getLogger(LoggingAsyncExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);
        work.behaviour = op -> {
            throw new IllegalStateException("smtp unreachable");
        };
        QuizNotificationService notifications = context().getBean(QuizNotificationService.class);

        try {
            // Act: does not throw in the caller
            notifications.sendQuizCreatedEmail("host1", "Java Basics");

            // Assert: the failure is recorded, not swallowed
            await().atMost(WAIT_SECONDS, TimeUnit.SECONDS).until(() -> !appender.list.isEmpty());
            ILoggingEvent event = appender.list.get(0);
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("QuizNotificationService.sendQuizCreatedEmail");
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("smtp unreachable");
        } finally {
            handlerLogger.detachAppender(appender);
        }
    }

    // ---------- waiting is always bounded ----------

    @Test
    void buildReport_slowCallNeverFinishes_givesUpAfterTimeoutWith504() {
        // Arrange: stats never answers; the caller may only wait 200 ms
        CountDownLatch neverReleasedDuringTest = latch();
        work.behaviour = op -> {
            if (op.equals("stats")) {
                blockUntil(neverReleasedDuringTest);
            }
        };
        QuizReportService reports = context(Duration.ofMillis(200), null, null).getBean(QuizReportService.class);

        // Act + Assert
        assertThatThrownBy(() -> reports.buildReport(QUIZ_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(504);
                    assertThat(e.getReason()).contains("too long");
                });
    }

    // ---------- executor overflow is an explicit decision ----------

    @Test
    void buildReport_reportExecutorFull_throwsTaskRejectedException() {
        // Arrange: a pool of 1 thread, no queue; the first call occupies it
        CountDownLatch busy = latch();
        work.behaviour = op -> blockUntil(busy);
        QuizReportService reports = context(Duration.ofSeconds(WAIT_SECONDS),
                tinyExecutor("tiny-", new ThreadPoolExecutor.AbortPolicy()), null)
                .getBean(QuizReportService.class);

        // Act + Assert: pdf is accepted, stats has nowhere to go -> rejected (mapped to 503 by the web layer)
        assertThatThrownBy(() -> reports.buildReport(QUIZ_ID)).isInstanceOf(TaskRejectedException.class);
    }

    @Test
    void sendEmail_notificationExecutorFull_runsInCallerThreadInsteadOfFailing() throws Exception {
        // Arrange: first email blocks the only worker; later ones run without blocking
        CountDownLatch release = latch();
        CountDownLatch firstStarted = latch();
        AtomicInteger calls = new AtomicInteger();
        work.behaviour = op -> {
            if (calls.incrementAndGet() == 1) {
                firstStarted.countDown();
                blockUntil(release);
            }
        };
        QuizNotificationService notifications = context(Duration.ofSeconds(WAIT_SECONDS), null,
                tinyExecutor("tiny-notify-", new ThreadPoolExecutor.CallerRunsPolicy()))
                .getBean(QuizNotificationService.class);
        notifications.sendQuizCreatedEmail("host1", "first");
        assertThat(firstStarted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        // Act: the pool is saturated -> CallerRunsPolicy
        notifications.sendQuizCreatedEmail("host1", "second");

        // Assert: no exception, and the second email ran on this (calling) thread
        assertThat(work.threadByOperation.get("email")).isEqualTo(Thread.currentThread().getName());
    }

    private static void blockUntil(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_SECONDS * 2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch not released");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
