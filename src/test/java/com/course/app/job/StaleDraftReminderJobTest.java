package com.course.app.job;

import com.course.app.config.QuartzConfig;
import com.course.app.service.StaleDraftReminderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaleDraftReminderJobTest {

    @Mock
    StaleDraftReminderService service;

    private final QuartzConfig config = new QuartzConfig();

    @Test
    void readsOlderThanDaysFromTheJobDataMap_andCallsTheServiceOnce() throws Exception {
        var context = JobContexts.of(config.staleDraftReminderJobDetail(), config.staleDraftReminderTrigger(), 0);

        new StaleDraftReminderJob(service).execute(context);

        verify(service).remind(7);
    }

    @Test
    void missingKey_usesTheDocumentedDefault() throws Exception {
        new StaleDraftReminderJob(service).execute(JobContexts.of(Map.of(), 0));

        verify(service).remind(7);
    }

    @Test
    void invalidParameter_failsExplicitly_withoutRetry() {
        when(service.remind(anyInt())).thenThrow(new IllegalArgumentException("olderThanDays must not be negative"));
        var context = JobContexts.of(Map.of("olderThanDays", -1), 0);

        assertThatThrownBy(() -> new StaleDraftReminderJob(service).execute(context))
                .isInstanceOfSatisfying(JobExecutionException.class,
                        e -> assertThat(e.refireImmediately()).isFalse());
        verify(service).remind(-1);
    }

    @Test
    void transientFailure_isRetriedWhileUnderTheLimit() {
        when(service.remind(anyInt())).thenThrow(new IllegalStateException("db down"));

        for (int refireCount = 0; refireCount < StaleDraftReminderJob.MAX_REFIRES; refireCount++) {
            var context = JobContexts.of(Map.of(), refireCount);
            assertThatThrownBy(() -> new StaleDraftReminderJob(service).execute(context))
                    .isInstanceOfSatisfying(JobExecutionException.class,
                            e -> assertThat(e.refireImmediately()).isTrue());
        }
    }

    @Test
    void transientFailure_stopsRetryingAfterTheLimit_soThereIsNoEndlessLoop() {
        when(service.remind(anyInt())).thenThrow(new IllegalStateException("db down"));
        var context = JobContexts.of(Map.of(), StaleDraftReminderJob.MAX_REFIRES);

        assertThatThrownBy(() -> new StaleDraftReminderJob(service).execute(context))
                .isInstanceOfSatisfying(JobExecutionException.class,
                        e -> assertThat(e.refireImmediately()).isFalse());
        verify(service, times(1)).remind(7);
    }

    @Test
    void jobMustNotRunConcurrentlyWithItself() {
        assertThat(StaleDraftReminderJob.class.isAnnotationPresent(DisallowConcurrentExecution.class)).isTrue();
    }
}
