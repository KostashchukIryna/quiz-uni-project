package com.course.scheduler.job;

import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;
import org.quartz.Trigger;

import java.util.Map;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/** Builds the JobExecutionContext Quartz would hand to a job, without starting a scheduler. */
final class JobContexts {

    private JobContexts() {
    }

    /** The JobDetail's map overlaid with the trigger's map (trigger wins), exactly what Quartz merges. */
    static JobExecutionContext of(JobDetail detail, Trigger trigger, int refireCount) {
        JobDataMap merged = new JobDataMap();
        merged.putAll(detail.getJobDataMap());
        merged.putAll(trigger.getJobDataMap());
        return of(merged, refireCount);
    }

    static JobExecutionContext of(Map<String, Object> data, int refireCount) {
        JobExecutionContext context = mock(JobExecutionContext.class);
        Scheduler scheduler = mock(Scheduler.class);
        try {
            lenient().when(scheduler.getContext()).thenReturn(new SchedulerContext());
        } catch (SchedulerException e) {
            throw new IllegalStateException(e);
        }
        lenient().when(context.getScheduler()).thenReturn(scheduler);
        lenient().when(context.getMergedJobDataMap()).thenReturn(new JobDataMap(data));
        lenient().when(context.getRefireCount()).thenReturn(refireCount);
        return context;
    }
}
