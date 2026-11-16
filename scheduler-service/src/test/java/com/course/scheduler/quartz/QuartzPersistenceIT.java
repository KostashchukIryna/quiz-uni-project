package com.course.scheduler.quartz;

import com.course.scheduler.config.QuartzConfig;
import com.course.scheduler.domain.HostRating;
import com.course.scheduler.service.HostRatingService;
import com.course.scheduler.service.StaleDraftReminderService;
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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Runs against a real Postgres (Docker) with this service's real Liquibase changelog and the real Quartz JDBC job
 * store. The quiz and question tables belong to quiz-service, so the test creates a minimal copy of them.
 * Executed by failsafe in {@code mvn verify}; skipped (not failed) when Docker is not available.
 */
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@Testcontainers(disabledWithoutDocker = true)
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
    HostRatingService ratingService;
    @Autowired
    StaleDraftReminderService reminderService;

    @BeforeEach
    void pauseScheduledFirings() throws Exception {
        jdbc.execute("""
                create table if not exists quiz (
                    id uuid primary key, title varchar(200) not null, description varchar(1000),
                    status varchar(20) not null, created_by varchar(100) not null, created_at timestamp not null)
                """);
        jdbc.execute("create table if not exists question (id uuid primary key, quiz_id uuid not null)");
        scheduler.standby();      // so the cron triggers cannot interfere with the assertions below
    }

    @AfterEach
    void cleanUp() throws Exception {
        jdbc.update("delete from question");
        jdbc.update("delete from quiz");
        jdbc.update("delete from host_rating");
        jdbc.update("delete from draft_reminder");
        scheduler.start();
    }

    @Test
    void quartzTables_existThroughLiquibase_andSpringBootDoesNotCreateThem() {
        Integer tables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name like 'qrtz\\_%'", Integer.class);
        Integer changesets = jdbc.queryForObject(
                "select count(*) from databasechangelog_scheduler where id = '003-create-quartz-tables'", Integer.class);

        assertThat(tables).isEqualTo(11);
        assertThat(changesets).isEqualTo(1);
        assertThat(environment.getProperty("spring.quartz.jdbc.initialize-schema")).isEqualTo("never");
        assertThat(environment.getProperty("spring.quartz.job-store-type")).isEqualTo("jdbc");
    }

    @Test
    void theService_createsOnlyItsOwnTables_andKeepsItsOwnLiquibaseBookkeeping() {
        List<String> own = jdbc.queryForList(
                "select table_name from information_schema.tables where table_name in ('host_rating', 'draft_reminder')",
                String.class);
        Integer sharedBookkeeping = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'databasechangelog'", Integer.class);

        assertThat(own).containsExactlyInAnyOrder("host_rating", "draft_reminder");
        assertThat(sharedBookkeeping).isZero();     // quiz-service's bookkeeping table is not touched or needed
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
        insertQuiz("host1", "PUBLISHED", Instant.now());
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
        UUID published = insertQuiz("host1", "PUBLISHED", Instant.now());
        insertQuiz("host1", "DRAFT", Instant.now());
        insertQuiz("host2", "DRAFT", Instant.now());
        insertQuestion(published);
        insertQuestion(published);

        ratingService.recalculate("IT_PERIOD", 0);
        List<HostRating> first = ratingService.leaderboard("IT_PERIOD");
        ratingService.recalculate("IT_PERIOD", 0);
        List<HostRating> second = ratingService.leaderboard("IT_PERIOD");

        assertThat(first).hasSize(2);
        assertThat(first.get(0).getUsername()).isEqualTo("host1");
        assertThat(first.get(0).getQuizzesTotal()).isEqualTo(2);
        assertThat(first.get(0).getQuestionsTotal()).isEqualTo(2);
        assertThat(first.get(0).getScore()).isEqualTo(2 + 2 + 2);     // 2 quizzes + 2 * 1 published + 2 questions
        assertThat(second).usingRecursiveComparison().ignoringFields("computedAt").isEqualTo(first);
    }

    @Test
    void reminderJob_isIdempotent_onARealDatabase() {
        Instant tenDaysAgo = Instant.now().minus(10, ChronoUnit.DAYS);
        UUID stale = insertQuiz("host1", "DRAFT", tenDaysAgo);                 // stale draft: gets a reminder
        insertQuiz("host2", "DRAFT", Instant.now());                           // too young
        insertQuiz("host3", "PUBLISHED", tenDaysAgo);                          // old but already published

        assertThat(reminderService.remind(7)).isEqualTo(1);
        assertThat(reminderService.remind(7)).isZero();                        // second run changes nothing

        assertThat(jdbc.queryForList("select quiz_id from draft_reminder", UUID.class)).containsExactly(stale);
    }

    private UUID insertQuiz(String host, String status, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into quiz(id, title, status, created_by, created_at) values (?, ?, ?, ?, ?)",
                id, "quiz of " + host, status, host, Timestamp.from(createdAt));
        return id;
    }

    private void insertQuestion(UUID quizId) {
        jdbc.update("insert into question(id, quiz_id) values (?, ?)", UUID.randomUUID(), quizId);
    }
}
