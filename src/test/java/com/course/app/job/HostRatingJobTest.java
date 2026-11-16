package com.course.app.job;

import com.course.app.config.QuartzConfig;
import com.course.app.service.HostRatingService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HostRatingJobTest {

    @Mock
    HostRatingService service;

    private final QuartzConfig config = new QuartzConfig();

    @Test
    void recentTrigger_overridesPeriodAndWindow_andCallsTheServiceOnce() throws Exception {
        var context = JobContexts.of(config.hostRatingJobDetail(), config.hostRatingRecentTrigger(), 0);

        new HostRatingJob(service).execute(context);

        verify(service).recalculate("LAST_7_DAYS", 7);
        verifyNoMoreInteractions(service);
    }

    @Test
    void allTimeTrigger_usesTheDefaultsOfTheJob() throws Exception {
        var context = JobContexts.of(config.hostRatingJobDetail(), config.hostRatingAllTimeTrigger(), 0);

        new HostRatingJob(service).execute(context);

        verify(service).recalculate("ALL_TIME", 0);
    }

    @Test
    void misspelledKey_isIgnored_andTheDocumentedDefaultIsUsed() throws Exception {
        var context = JobContexts.of(Map.of("windwDays", 30), 0);   // typo: the real key is windowDays

        new HostRatingJob(service).execute(context);

        verify(service).recalculate("ALL_TIME", 0);
    }

    @Test
    void failure_isRecorded_andNotRetried() {
        when(service.recalculate(anyString(), anyInt())).thenThrow(new IllegalStateException("db down"));
        var context = JobContexts.of(Map.of(), 0);

        assertThatThrownBy(() -> new HostRatingJob(service).execute(context))
                .isInstanceOfSatisfying(JobExecutionException.class,
                        e -> assertThat(e.refireImmediately()).isFalse())
                .hasRootCauseMessage("db down");
    }

    @Test
    void jobMustNotRunConcurrentlyWithItself() {
        assertThat(HostRatingJob.class.isAnnotationPresent(DisallowConcurrentExecution.class)).isTrue();
    }
}
