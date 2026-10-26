# Lab 5 — Async processing

## Three slow side effects (simulated ~1 s each via `SlowWork`)

| # | Operation | Class | Style | Result needed by caller? |
|---|---|---|---|---|
| 1 | E-mail to the host after a quiz is created | `QuizNotificationService.sendQuizCreatedEmail` (`@Async void`) | fire-and-forget, called from `QuizService.create` | no |
| 2 | PDF generation of a quiz | `QuizPdfService.render` (`@Async CompletableFuture<byte[]>`) | concurrent | yes |
| 3 | Call to an external analytics service | `QuizStatsService.fetch` (`@Async CompletableFuture<QuizStats>`) | concurrent | yes |

`QuizReportService.buildReport` starts #2 and #3 together and merges them with `thenCombine` into a `QuizReport`
(`GET /api/quizzes/{id}/report`, authenticated).

## Executors (`AsyncConfig`, explicit `ThreadPoolTaskExecutor` beans; every `@Async` names its executor)

| Bean | core / max / queue | Thread prefix | When full |
|---|---|---|---|
| `reportExecutor` | 2 / 4 / 10 | `report-` | `AbortPolicy` -> `TaskRejectedException` -> **503 + `Retry-After: 5`** "try again later" (`GlobalExceptionHandler`) |
| `notificationExecutor` | 1 / 2 / 50 | `notify-` | `CallerRunsPolicy` -> mail is sent in the caller's thread (never lost, request never fails) |

## Scenario -> where it is covered

| Scenario | Test (`AsyncProcessingTest` unless noted) |
|---|---|
| Slow method does not block the caller, finishes later on own executor | `render_slowWorkStillRunning_callReturnsImmediately...`, `sendEmail_slowWorkStillRunning_...` |
| Executor bean with explicit sizes / queue / prefix; not default pool | `executors_areExplicitlyBoundedAndNamed`, thread-name asserts (`report-`, not `task-`/`SimpleAsyncTaskExecutor`) |
| Two calls really run concurrently, results combined | `buildReport_pdfAndStatsAreIndependent_runConcurrentlyAndAreCombined` (both calls wait at a `CyclicBarrier(2)` — only passes if they run at the same time) |
| Failed async call does not fail the caller (`exceptionally`) | `buildReport_statsCallFails_...`, `..._pdfFails_...`, `..._bothCallsFail_...` |
| `void` @Async failure recorded by `AsyncUncaughtExceptionHandler` | `sendEmail_voidAsyncMethodThrows_callerIsUnaffectedAndHandlerLogsTheError` |
| Waiting is time-bounded | `buildReport_slowCallNeverFinishes_givesUpAfterTimeoutWith504` (`get(timeout)`, default 3 s) |
| Executor overflow handled deliberately, not 500 | `buildReport_reportExecutorFull_throwsTaskRejectedException`, `sendEmail_notificationExecutorFull_runsInCallerThread...`, `AsyncWebTest.report_executorRejectsTask_responds503TryAgainLaterNot500` |

Tests use a minimal Spring context (real proxies and real `AsyncConfig`, no Boot, no DB) and no `Thread.sleep`
— timing is driven by latches/barriers.

## Live run (against Keycloak + Postgres)
```
POST /api/quizzes            -> 201 in 0.34 s   (email NOT awaited)
   ... 1 s later in the log:  [notify-1] Email sent to host1: quiz 'Java Basics' was created
GET  /api/quizzes/{id}/report -> 200 in 1.05 s  (two 1 s calls ran concurrently; sequential would be ~2 s)
GET  .../report, no token     -> 401
30 parallel report requests   -> 4 x 200, 24 x 503 (pool full, "try again later"), 2 x 504 (timeout), 0 x 500
```
