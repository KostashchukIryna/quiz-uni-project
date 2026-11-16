package com.course.app.quartz;

import com.course.app.config.QuartzConfig;
import com.course.app.domain.Quiz;
import com.course.app.domain.QuizStatus;
import com.course.app.domain.HostRating;
import com.course.app.service.HostRatingService;
import com.course.app.service.StaleDraftReminderService;
import com.course.app.repository.QuizRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Runs against a real Postgres (Docker) with the real Liquibase changelog and the real Quartz JDBC job store.
 * Executed by failsafe in {@code mvn verify}; {@code mvn test} does not need Docker.
 */
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@Testcontainers
class QuartzPersistenceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Environment environment;
    @Autowired
    Scheduler scheduler;
    @Autowired
    QuizRepository quizRepository;
    @Autowired
    HostRatingService ratingService;
    @Autowired
    StaleDraftReminderService reminderService;

    @BeforeEach
    void pauseScheduledFirings() throws Exception {
        scheduler.standby();      // so the cron triggers cannot interfere with the assertions below
    }

    @AfterEach
    void cleanUp() throws Exception {
        quizRepository.deleteAll();
        jdbc.update("delete from host_rating");
        scheduler.start();
    }

    @Test
    void quartzTables_existThroughLiquibase_andSpringBootDoesNotCreateThem() {
        Integer tables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name like 'qrtz\\_%'", Integer.class);
        Integer changesets = jdbc.queryForObject(
                "select count(*) from databasechangelog where id = '005-create-quartz-tables'", Integer.class);

        assertThat(tables).isEqualTo(11);
        assertThat(changesets).isEqualTo(1);
        assertThat(environment.getProperty("spring.quartz.jdbc.initialize-schema")).isEqualTo("never");
        assertThat(environment.getProperty("spring.quartz.job-store-type")).isEqualTo("jdbc");
    }

    @Test
    void jobsAndTriggers_areStoredInTheDatabase_withTheirMisfireInstructionsAndTimeZone() {
        Map<String, Integer> misfire = jdbc.queryForList(
                        "select trigger_name, misfire_instr from qrtz_triggers where trigger_group = ?", QuartzConfig.GROUP)
                .stream().collect(Collectors.toMap(r -> (String) r.get("trigger_name"),
                        r -> (Integer) r.get("misfire_instr")));

        assertThat(misfire).containsOnlyKeys("hostRatingAllTimeTrigger", "hostRatingRecentTrigger",
                "staleDraftReminderTrigger");
        assertThat(misfire.get("hostRatingAllTimeTrigger")).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
        assertThat(misfire.get("hostRatingRecentTrigger")).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
        assertThat(misfire.get("staleDraftReminderTrigger")).isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);

        List<String> zones = jdbc.queryForList("select distinct time_zone_id from qrtz_cron_triggers", String.class);
        assertThat(zones).containsExactly("Europe/Kyiv");

        List<Map<String, Object>> jobs = jdbc.queryForList(
                "select job_name, is_durable, is_nonconcurrent from qrtz_job_details where job_group = ?",
                QuartzConfig.GROUP);
        assertThat(jobs).extracting(r -> r.get("job_name")).containsExactlyInAnyOrder("hostRatingJob", "staleDraftReminderJob");
        assertThat(jobs).allSatisfy(r -> {
            assertThat(r.get("is_durable")).isEqualTo(true);
            assertThat(r.get("is_nonconcurrent")).isEqualTo(true);     // @DisallowConcurrentExecution
        });
    }

    @Test
    void ratingJob_runsThroughQuartz_andTheTriggerDataOverridesTheJobDefaults() throws Exception {
        saveQuiz("host1", QuizStatus.PUBLISHED, Instant.now());
        scheduler.start();

        JobDataMap override = new JobDataMap();
        override.put("period", "IT_TRIGGERED");
        scheduler.triggerJob(QuartzConfig.HOST_RATING_JOB, override);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(ratingService.leaderboard("IT_TRIGGERED")).extracting(HostRating::getUsername)
                        .containsExactly("host1"));
    }

    @Test
    void ratingJob_isIdempotent_onARealDatabase() {
        saveQuiz("host1", QuizStatus.PUBLISHED, Instant.now());
        saveQuiz("host1", QuizStatus.DRAFT, Instant.now());
        saveQuiz("host2", QuizStatus.DRAFT, Instant.now());

        ratingService.recalculate("IT_PERIOD", 0);
        List<HostRating> first = ratingService.leaderboard("IT_PERIOD");
        ratingService.recalculate("IT_PERIOD", 0);
        List<HostRating> second = ratingService.leaderboard("IT_PERIOD");

        assertThat(first).hasSize(2);
        assertThat(first.get(0).getUsername()).isEqualTo("host1");
        assertThat(first.get(0).getScore()).isEqualTo(2 + 2);     // 2 quizzes + 2 * 1 published, no questions
        assertThat(second).usingRecursiveComparison().ignoringFields("computedAt").isEqualTo(first);
    }

    @Test
    void reminderJob_isIdempotent_onARealDatabase() {
        Instant tenDaysAgo = Instant.now().minus(10, ChronoUnit.DAYS);
        saveQuiz("host1", QuizStatus.DRAFT, tenDaysAgo);                 // stale draft: gets a reminder
        saveQuiz("host2", QuizStatus.DRAFT, Instant.now());              // too young
        saveQuiz("host3", QuizStatus.PUBLISHED, tenDaysAgo);             // old but already published

        assertThat(reminderService.remind(7)).isEqualTo(1);
        assertThat(reminderService.remind(7)).isZero();                  // second run changes nothing

        assertThat(quizRepository.findAll()).filteredOn(q -> q.getRemindedAt() != null)
                .extracting(Quiz::getCreatedBy).containsExactly("host1");
    }

    private void saveQuiz(String host, QuizStatus status, Instant createdAt) {
        Quiz quiz = new Quiz();
        quiz.setTitle("quiz of " + host);
        quiz.setCreatedBy(host);
        quiz.setStatus(status);
        quiz.setCreatedAt(createdAt);
        quizRepository.save(quiz);
    }
}
