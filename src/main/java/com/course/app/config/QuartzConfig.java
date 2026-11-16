package com.course.app.config;

import com.course.app.job.HostRatingJob;
import com.course.app.job.StaleDraftReminderJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class QuartzConfig {

    public static final String GROUP = "quizplatform";
    public static final JobKey HOST_RATING_JOB = JobKey.jobKey("hostRatingJob", GROUP);
    public static final JobKey STALE_DRAFT_REMINDER_JOB = JobKey.jobKey("staleDraftReminderJob", GROUP);
    private static final TimeZone ZONE = TimeZone.getTimeZone("Europe/Kyiv");

    @Bean
    public JobDetail hostRatingJobDetail() {
        return JobBuilder.newJob(HostRatingJob.class)
                .withIdentity(HOST_RATING_JOB)
                .withDescription("Recomputes the host leaderboard")
                .usingJobData("period", "ALL_TIME")
                .usingJobData("windowDays", 0)
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger hostRatingAllTimeTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(HOST_RATING_JOB)
                .withIdentity("hostRatingAllTimeTrigger", GROUP)
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 3 * * ?")
                        .inTimeZone(ZONE)
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }

    @Bean
    public Trigger hostRatingRecentTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(HOST_RATING_JOB)
                .withIdentity("hostRatingRecentTrigger", GROUP)
                .usingJobData("period", "LAST_7_DAYS")
                .usingJobData("windowDays", 7)
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0/2 * * * ?")
                        .inTimeZone(ZONE)
                        .withMisfireHandlingInstructionDoNothing())
                .build();
    }

    @Bean
    public JobDetail staleDraftReminderJobDetail() {
        return JobBuilder.newJob(StaleDraftReminderJob.class)
                .withIdentity(STALE_DRAFT_REMINDER_JOB)
                .withDescription("Reminds hosts about drafts that were never published")
                .usingJobData("olderThanDays", 7)
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger staleDraftReminderTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(STALE_DRAFT_REMINDER_JOB)
                .withIdentity("staleDraftReminderTrigger", GROUP)
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0/5 * * * ?")
                        .inTimeZone(ZONE)
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }
}
