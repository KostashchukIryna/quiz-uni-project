package com.course.common;

import java.time.Instant;
import java.util.UUID;

/**
 * The message quiz-service publishes when something happens to a quiz and notification-service consumes.
 * It is the only thing the two services share: a plain data object that is serialized to JSON on the wire.
 *
 * @param eventId unique per event; the consumer uses it to recognize a message it has already processed
 * @param host    owner of the quiz, the person the event is about
 */
public record QuizEvent(UUID eventId, Type type, UUID quizId, String title, String host, Instant occurredAt) {

    /** Topic exchange the events are published to. Its queues and bindings are provisioned by the broker. */
    public static final String EXCHANGE = "quiz.events";

    public enum Type {
        QUIZ_CREATED, QUIZ_PUBLISHED, QUIZ_DELETED;

        /** {@code QUIZ_CREATED} is routed with the key {@code quiz.created}. */
        public String routingKey() {
            return "quiz." + name().substring("QUIZ_".length()).toLowerCase();
        }
    }

    public static QuizEvent of(Type type, UUID quizId, String title, String host, Instant occurredAt) {
        return new QuizEvent(UUID.randomUUID(), type, quizId, title, host, occurredAt);
    }

    public String routingKey() {
        return type.routingKey();
    }
}
