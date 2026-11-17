# Lab 8: Message brokers

`quiz-service` (producer) and `notification-service` (consumer) talk only through RabbitMQ. Neither calls the other
over HTTP and the consumer uses none of the producer's classes.

```text
 quiz-service :8081                         RabbitMQ                          notification-service :8083
 ┌──────────────────────┐   after commit   ┌────────────────────────────┐    ┌───────────────────────────┐
 │ create / publish /   │ ───────────────► │ exchange quiz.events (topic)│    │ @RabbitListener           │
 │ delete a quiz        │   QuizEvent JSON │   │ quiz.*                  │    │   idempotent handler      │
 └──────────────────────┘                  │   ▼                         │───►│   notification table      │
                                           │ queue notification.quiz-events│  │ GET /api/notifications    │
        common: QuizEvent (the only        │   │ rejected                │    └───────────────────────────┘
        shared class)                      │   ▼                         │
                                           │ dead-letter queue (…​.dlq)   │
                                           └────────────────────────────┘
```

## The three business actions

| Action in quiz-service | Event | What notification-service stores for the quiz owner |
|---|---|---|
| Create a quiz (`POST /api/quizzes`) | `QUIZ_CREATED` | "Your quiz 'X' was created" |
| Publish a quiz (`PUT`, status `DRAFT` -> `PUBLISHED`) | `QUIZ_PUBLISHED` | "Your quiz 'X' is now published" |
| Delete a quiz (`DELETE`, ADMIN) | `QUIZ_DELETED` | "Your quiz 'X' was deleted" |

The result is visible at `GET /api/notifications` on notification-service (the caller's own notifications, newest first,
Keycloak JWT required). Editing a quiz without changing its status publishes nothing.

## Scenario by scenario

| Scenario | How it is satisfied | Proof |
|---|---|---|
| 1. One action, one event, visible result | `QuizService` raises one `QuizEvent`; `QuizEventPublisher` sends it; `QuizEventListener` stores a `notification` row | `QuizEventsIT.createQuiz_publishesExactlyOneEvent_toTheDurableQueue`, `NotificationConsumerIT.publishedEvent_isReceived_andTheResultIsVisible`, live run below |
| 2. No event on failure, publish after commit | the event is a Spring application event delivered by `@TransactionalEventListener(AFTER_COMMIT)`; a rollback means the listener never runs | `QuizEventTransactionTest` (the test records whether the transaction had committed when the event was sent; it fails if the phase is changed to `BEFORE_COMMIT`), `QuizEventsIT.rolledBackAction_publishesNothing_andSavesNothing`, `QuizServiceTest` (no event when the repository fails or the quiz is missing) |
| 3. No HTTP between services, no producer classes in the consumer | the services share only `common.QuizEvent`. The build fails if one service depends on the other (enforcer rule), and neither service uses an HTTP client | `mvn dependency:tree -pl notification-service` lists only `common`; `grep -rE "RestTemplate\|RestClient\|WebClient\|HttpClient" */src/main` finds nothing |
| 4. Consumer stopped: producer still answers; both events processed after restart | the queue is durable and exists before any consumer runs (the broker creates it, see below), messages are persistent | `NotificationConsumerIT.consumerStopped_eventsWaitInTheQueue_andBothAreProcessedAfterItStartsAgain`, live run below |
| 5. Same event again changes nothing (unit test) | `NotificationService.handle` skips an event id it has seen; the table also has a unique constraint on `event_id` | `NotificationServiceTest.sameEventDeliveredTwice_isStoredOnce_andTheSecondDeliveryChangesNothing`, `QuizEventListenerTest.onEvent_raceOnTheUniqueEventId_isTreatedAsADuplicate`, `NotificationConsumerIT.sameEventDeliveredTwice_isStoredOnce` |
| 6. Broker address from the environment | `spring.rabbitmq.host: ${RABBITMQ_HOST:localhost}` in both services; compose sets `RABBITMQ_HOST=rabbitmq` | `MessagingConfigTest` in both services (default `localhost`, override `rabbitmq`), live run in containers |

## Broker (compose.yaml)

`rabbitmq:3.13-management` (AMQP on 5672, management UI on http://localhost:15672, login `guest` / `guest`).
`rabbitmq/definitions.json`, loaded by the broker at startup (`rabbitmq/rabbitmq.conf`), creates:

| Entity | Purpose |
|---|---|
| exchange `quiz.events` (topic, durable) | producers publish here with routing key `quiz.created`, `quiz.published` or `quiz.deleted` |
| queue `notification.quiz-events` (durable), bound with `quiz.*` | the consumer's inbox; rejected messages go to the dead-letter exchange |
| exchange `quiz.events.dlx` and queue `notification.quiz-events.dlq` | where a message that cannot be processed is parked, not lost or retried forever |

The topology lives in the broker (not created by whichever service starts first) so that an event published while the
consumer is down, or has never run, is kept: without the queue the broker would drop it as unroutable.
The same file is loaded into the Testcontainers brokers of the integration tests.

## Reliability

- **Producer:** durable exchange, persistent messages (Spring AMQP default), publisher confirms and returns turned on
  (a nack or an unroutable event is logged as an error).
- **Consumer:** the message is acknowledged when the listener returns. On an exception the listener retries 3 times
  (0.5 s, 1 s) and then rejects the message without requeue, so the broker moves it to the dead-letter queue.
  An unreadable message is rejected at once (tested: it lands in the dead-letter queue and does not block the next event).
- **Idempotency:** delivery is at-least-once, so the consumer must tolerate duplicates. It checks `event_id` before
  saving and the database enforces uniqueness for a concurrent duplicate.

## Known limit: the dual write

The database commit and the broker publish are two separate systems. Publishing happens right after the commit. If the
service dies in that moment, or the broker is unreachable, the quiz change is saved but its event is not sent. The
publisher then logs an error with the event id and does not fail the HTTP request (the data is already committed).
The complete fix is the *transactional outbox*: write the event into an `outbox` table in the same transaction and let
a relay publish it. That is not implemented here.

## How to run

Everything in containers (services reach each other's infrastructure by compose service name):

```bash
docker compose --profile apps up --build
```

From the IDE or Maven (infrastructure from compose, addresses default to `localhost`):

```bash
mvn -pl .,common -DskipTests install       # once
mvn -pl quiz-service spring-boot:run
mvn -pl scheduler-service spring-boot:run
mvn -pl notification-service spring-boot:run
```

Try it (tokens as in `postman/quizplatform.http`):

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/realms/quizplatform/protocol/openid-connect/token \
  -d grant_type=password -d client_id=quiz-postman -d username=host1 -d password=host123 | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
curl -s -X POST http://localhost:8081/api/quizzes -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Demo","status":"DRAFT","questions":[]}'
curl -s http://localhost:8083/api/notifications -H "Authorization: Bearer $TOKEN"
```

Scenario 4 by hand: `docker compose stop notification-service`, create two quizzes (both answer 201), look at the
queue (`docker exec <rabbitmq container> rabbitmqctl list_queues name messages` shows 2), then
`docker compose start notification-service` and read `/api/notifications`.

**If you ran an earlier lab:** use a fresh database (`docker compose down`), because the services now create their own tables.

## Result of the run done while writing this lab

Containers built and started with `docker compose --profile apps up`, real Keycloak tokens, real HTTP:

```text
Scenario 1:  create quiz   -> QUIZ_CREATED   | Your quiz 'Scenario one' was created
             publish       -> QUIZ_PUBLISHED | Your quiz 'Scenario one' is now published
             delete (admin)-> QUIZ_DELETED   | Your quiz 'Scenario one' was deleted
Scenario 4:  notification-service stopped, two POST -> 201 and 201, queue holds 2 messages,
             after start the queue holds 0 and both notifications are listed
Failure:     POST with an empty title -> 400, notification count unchanged (5 before, 5 after)
```

## Theory

- **Synchronous vs asynchronous.** An HTTP call makes the caller wait and fail when the callee is down. A message broker
  decouples them in time: the sender hands the message to the broker and continues; the receiver takes it when it can.
- **AMQP model (RabbitMQ).** A *producer* publishes to an *exchange*, never directly to a queue. *Bindings* connect an
  exchange to *queues* by a rule (a *topic* exchange matches routing keys such as `quiz.*`). A *consumer* reads from a
  queue and *acknowledges* each message; an unacknowledged message is redelivered. A message nobody can route is dropped
  (unless it is published *mandatory*, then it is returned and we log it).
- **Durability.** A message survives a broker restart only if the exchange and queue are durable *and* the message is
  persistent. Both are on here.
- **Delivery guarantees.** *At-most-once* can lose messages, *at-least-once* never loses but can duplicate,
  *exactly-once* is not achievable end to end in practice. Reliable systems use at-least-once with an
  *idempotent consumer*, which is scenario 5.
- **Idempotency.** Processing the same message twice has the same effect as once. Here: a unique event id, checked before
  the write and enforced by a unique constraint.
- **Why after commit.** An event sent inside the transaction can describe data that is then rolled back, and the consumer
  may even read the database before the commit is visible. `@TransactionalEventListener(AFTER_COMMIT)` runs only once the
  commit has happened. `BEFORE_COMMIT` runs before it, and with no active transaction the listener is skipped.
- **Dual-write problem and outbox.** Two systems cannot be updated atomically. The outbox pattern turns it into one
  atomic write (the row in the same transaction) plus a relay that retries publishing.
- **Dead-letter queue.** A message that keeps failing would block the queue or loop forever. After a limited number of
  attempts it is moved aside so the rest keeps flowing and the failed message can be inspected or replayed.
- **Shared event class.** The only code both sides use is the data object `QuizEvent`, serialized as JSON. The consumer
  reads the type from its own listener method, so no producer class name travels in the message.
- **RabbitMQ or Kafka.** RabbitMQ is a queue: a message is delivered to a consumer and removed after the ack, which fits
  "deliver this task once". Kafka is a replayable log with consumer offsets, better for streams and large volumes. Nothing
  in this lab needs replay, so the lighter broker was chosen.

## Questions you may get

- *How do you prove nothing is lost when the consumer is down?* The queue is durable and created by the broker; the test
  and the live run stop the consumer, publish two events, see both in the queue, restart and see both processed.
- *What if the same message arrives twice?* The consumer recognizes the event id and does nothing the second time
  (unit test, integration test).
- *Why publish after the commit and not inside the transaction?* Otherwise a rollback leaves an event about something
  that never happened. The test would fail if the phase were `BEFORE_COMMIT`.
- *What if the broker is down at that moment?* The data is saved, the request succeeds, the error is logged with the
  event id. This is the dual-write gap; the outbox pattern closes it.
- *Why no shared service classes?* Services would then deploy together. Only the event data object is shared, and the build
  enforces that services do not depend on each other.
- *What happens to a message that cannot be processed?* Three attempts, then the dead-letter queue.
- *Why does the broker create the queue and not the services?* So the queue exists even if the consumer has never started.
