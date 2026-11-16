package com.course.scheduler.job;

import com.course.scheduler.service.StaleDraftReminderService;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Setter
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class StaleDraftReminderJob extends QuartzJobBean {

    static final int MAX_REFIRES = 3;

    private static final Logger log = LoggerFactory.getLogger(StaleDraftReminderJob.class);

    private final StaleDraftReminderService reminderService;

    private int olderThanDays = 7;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            int sent = reminderService.remind(olderThanDays);
            log.info("StaleDraftReminderJob: olderThanDays={} -> {} reminder(s)", olderThanDays, sent);
        } catch (IllegalArgumentException e) {
            log.error("StaleDraftReminderJob has an invalid parameter (olderThanDays={}); not retrying",
                    olderThanDays, e);
            throw new JobExecutionException(e, false);
        } catch (RuntimeException e) {
            int refires = context.getRefireCount();
            if (refires < MAX_REFIRES) {
                log.warn("StaleDraftReminderJob failed, retry {} of {}", refires + 1, MAX_REFIRES, e);
                throw new JobExecutionException(e, true);
            }
            log.error("StaleDraftReminderJob failed after {} retries; waiting for the next scheduled run",
                    MAX_REFIRES, e);
            throw new JobExecutionException(e, false);
        }
    }
}
