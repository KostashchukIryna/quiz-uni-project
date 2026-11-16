package com.course.app.job;

import com.course.app.service.HostRatingService;
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
public class HostRatingJob extends QuartzJobBean {

    private static final Logger log = LoggerFactory.getLogger(HostRatingJob.class);

    private final HostRatingService ratingService;

    private String period = "ALL_TIME";
    private int windowDays = 0;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            int written = ratingService.recalculate(period, windowDays);
            log.info("HostRatingJob: period={} windowDays={} -> {} rating(s)", period, windowDays, written);
        } catch (RuntimeException e) {
            log.error("HostRatingJob failed (period={}, windowDays={}); waiting for the next scheduled run",
                    period, windowDays, e);
            throw new JobExecutionException(e, false);
        }
    }
}
