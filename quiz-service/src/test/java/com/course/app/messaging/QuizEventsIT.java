package com.course.app.messaging;

import com.course.app.dto.QuizDto;
import com.course.app.repository.QuizRepository;
import com.course.app.service.QuizService;
import com.course.common.QuizEvent;
import com.course.common.QuizStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Postgres and real RabbitMQ (Docker), the broker topology loaded from rabbitmq/definitions.json exactly as
 * in compose.yaml. There is no consumer in this test: the events wait in the durable queue, which is also the
 * producer half of "the producer answers even when the consumer is stopped".
 * Executed by failsafe in {@code mvn verify}; skipped (not failed) when Docker is not available.
 */
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class QuizEventsIT {

    private static final String QUEUE = "notification.quiz-events";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("..", "rabbitmq", "definitions.json")),
                    "/tmp/definitions.json");

    @BeforeAll
    static void loadBrokerTopology() throws Exception {
        var result = RABBIT.execInContainer("rabbitmqctl", "import_definitions", "/tmp/definitions.json");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }

    @Autowired
    QuizService quizService;
    @Autowired
    QuizRepository quizRepository;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    RabbitAdmin rabbitAdmin;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void emptyQueueAndLogInAsHost() {
        rabbitAdmin.purgeQueue(QUEUE, false);
        login("host1", "ROLE_HOST");
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        quizRepository.deleteAll();
    }

    private static void login(String user, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "n/a", List.of(new SimpleGrantedAuthority(role))));
    }

    private static QuizDto draft(String title) {
        return new QuizDto(null, title, "desc", QuizStatus.DRAFT, null, null, List.of());
    }

    private QuizEvent nextEvent() throws Exception {
        Message message = rabbitTemplate.receive(QUEUE, 5_000);
        assertThat(message).as("an event on the queue").isNotNull();
        return objectMapper.readValue(message.getBody(), QuizEvent.class);
    }

    private void assertNoMoreEvents() {
        assertThat(rabbitTemplate.receive(QUEUE, 1_000)).as("no further event").isNull();
    }

    @Test
    void createQuiz_publishesExactlyOneEvent_toTheDurableQueue() throws Exception {
        QuizDto created = quizService.create(draft("Java Basics"), "host1");

        QuizEvent event = nextEvent();
        assertThat(event.type()).isEqualTo(QuizEvent.Type.QUIZ_CREATED);
        assertThat(event.quizId()).isEqualTo(created.id());
        assertThat(event.title()).isEqualTo("Java Basics");
        assertThat(event.host()).isEqualTo("host1");
        assertNoMoreEvents();
    }

    @Test
    void publishQuiz_publishesOneEvent_butAnEditThatKeepsTheStatusPublishesNone() throws Exception {
        QuizDto created = quizService.create(draft("Spring"), "host1");
        nextEvent();                                                     // the creation event

        quizService.update(created.id(), new QuizDto(created.id(), "Spring (edited)", "desc",
                QuizStatus.DRAFT, null, null, List.of()));
        assertNoMoreEvents();                                            // still a draft: nothing happened

        quizService.update(created.id(), new QuizDto(created.id(), "Spring (edited)", "desc",
                QuizStatus.PUBLISHED, null, null, List.of()));
        QuizEvent event = nextEvent();
        assertThat(event.type()).isEqualTo(QuizEvent.Type.QUIZ_PUBLISHED);
        assertThat(event.title()).isEqualTo("Spring (edited)");
        assertThat(event.host()).isEqualTo("host1");
        assertNoMoreEvents();
    }

    @Test
    void deleteQuiz_publishesOneEvent_forTheOwner() throws Exception {
        QuizDto created = quizService.create(draft("Old quiz"), "host1");
        nextEvent();

        login("admin1", "ROLE_ADMIN");
        quizService.delete(created.id());

        QuizEvent event = nextEvent();
        assertThat(event.type()).isEqualTo(QuizEvent.Type.QUIZ_DELETED);
        assertThat(event.title()).isEqualTo("Old quiz");
        assertThat(event.host()).isEqualTo("host1");
        assertNoMoreEvents();
    }

    @Test
    void rolledBackAction_publishesNothing_andSavesNothing() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            quizService.create(draft("Never saved"), "host1");
            status.setRollbackOnly();
        });

        assertThat(quizRepository.count()).isZero();
        assertNoMoreEvents();
    }

    @Test
    void eachEventCarriesItsOwnId() throws Exception {
        quizService.create(draft("One"), "host1");
        quizService.create(draft("Two"), "host1");

        UUID first = nextEvent().eventId();
        UUID second = nextEvent().eventId();
        assertThat(first).isNotEqualTo(second);
    }
}
