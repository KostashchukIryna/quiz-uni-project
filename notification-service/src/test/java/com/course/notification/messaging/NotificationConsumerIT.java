package com.course.notification.messaging;

import com.course.common.QuizEvent;
import com.course.notification.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Real Postgres and real RabbitMQ (Docker), the broker topology loaded from rabbitmq/definitions.json exactly as
 * in compose.yaml. There is no producer service here: the test publishes to the exchange the way quiz-service does.
 * Executed by failsafe in {@code mvn verify}; skipped (not failed) when Docker is not available.
 */
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class NotificationConsumerIT {

    private static final String QUEUE = "notification.quiz-events";
    private static final String DLQ = "notification.quiz-events.dlq";
    private static final Duration WAIT = Duration.ofSeconds(20);

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
    NotificationRepository repository;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    RabbitAdmin rabbitAdmin;
    @Autowired
    RabbitListenerEndpointRegistry registry;

    private MessageListenerContainer listener() {
        return registry.getListenerContainer(QuizEventListener.LISTENER_ID);
    }

    @BeforeEach
    void cleanAndMakeSureTheConsumerIsRunning() {
        if (!listener().isRunning()) {
            listener().start();
        }
        rabbitAdmin.purgeQueue(QUEUE, false);
        rabbitAdmin.purgeQueue(DLQ, false);
        repository.deleteAll();
    }

    @AfterEach
    void restartConsumer() {
        if (!listener().isRunning()) {
            listener().start();
        }
    }

    private static QuizEvent event(QuizEvent.Type type, String title) {
        return QuizEvent.of(type, UUID.randomUUID(), title, "host1", Instant.now());
    }

    private void publish(QuizEvent event) {
        rabbitTemplate.convertAndSend(QuizEvent.EXCHANGE, event.routingKey(), event);
    }

    private int messagesIn(String queue) {
        return rabbitAdmin.getQueueInfo(queue).getMessageCount();
    }

    @Test
    void publishedEvent_isReceived_andTheResultIsVisible() {
        QuizEvent event = event(QuizEvent.Type.QUIZ_CREATED, "Java Basics");

        publish(event);

        await().atMost(WAIT).untilAsserted(() ->
                assertThat(repository.findByRecipientOrderByOccurredAtDesc("host1"))
                        .singleElement().satisfies(n -> {
                            assertThat(n.getEventId()).isEqualTo(event.eventId());
                            assertThat(n.getMessage()).isEqualTo("Your quiz 'Java Basics' was created");
                        }));
    }

    @Test
    void consumerStopped_eventsWaitInTheQueue_andBothAreProcessedAfterItStartsAgain() {
        listener().stop();
        QuizEvent first = event(QuizEvent.Type.QUIZ_CREATED, "First");
        QuizEvent second = event(QuizEvent.Type.QUIZ_PUBLISHED, "Second");

        publish(first);                                    // the publisher is not affected by the stopped consumer
        publish(second);

        await().atMost(WAIT).untilAsserted(() -> assertThat(messagesIn(QUEUE)).isEqualTo(2));
        assertThat(repository.count()).isZero();           // nothing processed while the consumer is down

        listener().start();

        await().atMost(WAIT).untilAsserted(() -> {
            assertThat(repository.existsByEventId(first.eventId())).isTrue();
            assertThat(repository.existsByEventId(second.eventId())).isTrue();
        });
        await().atMost(WAIT).untilAsserted(() -> assertThat(messagesIn(QUEUE)).isZero());
    }

    @Test
    void sameEventDeliveredTwice_isStoredOnce() {
        QuizEvent event = event(QuizEvent.Type.QUIZ_DELETED, "Old quiz");

        publish(event);
        publish(event);

        await().atMost(WAIT).untilAsserted(() -> assertThat(messagesIn(QUEUE)).isZero());
        await().atMost(WAIT).untilAsserted(() -> assertThat(repository.count()).isEqualTo(1));
    }

    @Test
    void unreadableMessage_isParkedInTheDeadLetterQueue_andDoesNotBlockTheNextEvent() {
        Message poison = MessageBuilder.withBody("this is not json".getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).build();
        rabbitTemplate.send(QuizEvent.EXCHANGE, "quiz.created", poison);

        await().atMost(WAIT).untilAsserted(() -> assertThat(messagesIn(DLQ)).isEqualTo(1));
        assertThat(messagesIn(QUEUE)).isZero();

        QuizEvent next = event(QuizEvent.Type.QUIZ_CREATED, "After the poison");
        publish(next);
        await().atMost(WAIT).untilAsserted(() -> assertThat(repository.existsByEventId(next.eventId())).isTrue());
    }
}
