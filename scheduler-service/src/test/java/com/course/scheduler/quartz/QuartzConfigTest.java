package com.course.scheduler.quartz;

import com.course.scheduler.config.QuartzConfig;
import com.course.scheduler.job.HostRatingJob;
import com.course.scheduler.job.StaleDraftReminderJob;
import org.junit.jupiter.api.Test;
import org.quartz.CronExpression;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Trigger;

import static org.assertj.core.api.Assertions.assertThat;

/** No Spring context: the config methods only build descriptions of jobs and triggers, so they can be inspected directly. */
class QuartzConfigTest {

    private final QuartzConfig config = new QuartzConfig();

    /** What Quartz hands to the job: the JobDetail's map overlaid with the trigger's map (trigger wins). */
    private JobDataMap merged(JobDetail detail, Trigger trigger) {
        JobDataMap merged = new JobDataMap();
        merged.putAll(detail.getJobDataMap());
        merged.putAll(trigger.getJobDataMap());
        return merged;
    }

    @Test
    void jobDetails_haveNameGroupAndClass_andAreDurable() {
        JobDetail rating = config.hostRatingJobDetail();
        JobDetail reminder = config.staleDraftReminderJobDetail();

        assertThat(rating.getKey()).isEqualTo(QuartzConfig.HOST_RATING_JOB);
        assertThat(rating.getKey().getName()).isEqualTo("hostRatingJob");
        assertThat(rating.getKey().getGroup()).isEqualTo("quizplatform");
        assertThat(rating.getJobClass()).isEqualTo(HostRatingJob.class);
        assertThat(rating.isDurable()).isTrue();

        assertThat(reminder.getKey()).isEqualTo(QuartzConfig.STALE_DRAFT_REMINDER_JOB);
        assertThat(reminder.getJobClass()).isEqualTo(StaleDraftReminderJob.class);
        assertThat(reminder.isDurable()).isTrue();
    }

    @Test
    void everyTrigger_pointsAtItsJob_andHasNameAndGroup() {
        assertThat(config.hostRatingAllTimeTrigger().getJobKey()).isEqualTo(QuartzConfig.HOST_RATING_JOB);
        assertThat(config.hostRatingRecentTrigger().getJobKey()).isEqualTo(QuartzConfig.HOST_RATING_JOB);
        assertThat(config.staleDraftReminderTrigger().getJobKey()).isEqualTo(QuartzConfig.STALE_DRAFT_REMINDER_JOB);

        for (Trigger t : new Trigger[]{config.hostRatingAllTimeTrigger(), config.hostRatingRecentTrigger(),
                config.staleDraftReminderTrigger()}) {
            assertThat(t.getKey().getName()).isNotBlank();
            assertThat(t.getKey().getGroup()).isEqualTo(QuartzConfig.GROUP);
        }
    }

    @Test
    void ratingJob_hasTwoTriggersWithDistinctNames() {
        assertThat(config.hostRatingAllTimeTrigger().getKey())
                .isNotEqualTo(config.hostRatingRecentTrigger().getKey());
    }

    @Test
    void allTimeTrigger_isNightly_andCatchesUpOnceAfterAMisfire() {
        CronTrigger trigger = (CronTrigger) config.hostRatingAllTimeTrigger();

        assertThat(trigger.getCronExpression()).isEqualTo("0 0 3 * * ?");
        assertThat(CronExpression.isValidExpression(trigger.getCronExpression())).isTrue();
        assertThat(trigger.getTimeZone().getID()).isEqualTo("Europe/Kyiv");
        assertThat(trigger.getMisfireInstruction()).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
    }

    @Test
    void recentTrigger_runsEveryTwoMinutes_andSkipsAMissedRun() {
        CronTrigger trigger = (CronTrigger) config.hostRatingRecentTrigger();

        assertThat(trigger.getCronExpression()).isEqualTo("0 0/2 * * * ?");
        assertThat(CronExpression.isValidExpression(trigger.getCronExpression())).isTrue();
        assertThat(trigger.getTimeZone().getID()).isEqualTo("Europe/Kyiv");
        assertThat(trigger.getMisfireInstruction()).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
    }

    @Test
    void reminderTrigger_catchesUpOnceAfterAMisfire() {
        CronTrigger trigger = (CronTrigger) config.staleDraftReminderTrigger();

        assertThat(trigger.getCronExpression()).isEqualTo("0 0/5 * * * ?");
        assertThat(CronExpression.isValidExpression(trigger.getCronExpression())).isTrue();
        assertThat(trigger.getTimeZone().getID()).isEqualTo("Europe/Kyiv");
        assertThat(trigger.getMisfireInstruction()).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
    }

    @Test
    void allTimeTrigger_inheritsBothDefaultsFromTheJob() {
        JobDataMap data = merged(config.hostRatingJobDetail(), config.hostRatingAllTimeTrigger());

        assertThat(data.getString("period")).isEqualTo("ALL_TIME");
        assertThat(data.getInt("windowDays")).isZero();
    }

    @Test
    void recentTrigger_valuesWinOverTheJobDefaults() {
        JobDataMap data = merged(config.hostRatingJobDetail(), config.hostRatingRecentTrigger());

        assertThat(data.getString("period")).isEqualTo("LAST_7_DAYS");   // trigger value wins
        assertThat(data.getInt("windowDays")).isEqualTo(7);              // trigger value wins
        assertThat(config.hostRatingJobDetail().getJobDataMap().getString("period"))
                .isEqualTo("ALL_TIME");                                   // the job's own default is untouched
    }

    @Test
    void reminderTrigger_keepsTheJobDefault() {
        JobDataMap data = merged(config.staleDraftReminderJobDetail(), config.staleDraftReminderTrigger());

        assertThat(data.getInt("olderThanDays")).isEqualTo(7);
    }
}
