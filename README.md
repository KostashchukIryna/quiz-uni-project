# QuizPlatform 

Kahoot-like quiz platform, which is a project for a Java course


## Lab 2 — unit tests
```bash
mvn verify        # runs tests, JaCoCo report (target/site/jacoco/index.html) and a 60% line-coverage gate
```
Tests live in `src/test/java` (JUnit 5 + Mockito + AssertJ, no Spring context). Main SUT: `QuizService`.
Mutation proof (AC4): `docs/mutation-evidence.md`. JaCoCo excludes generated `*MapperImpl` and `AppApplication`.

## Lab 7 — multi-module Maven
The repository is one Maven build with four modules: `common` (shared enum and event), `quiz-service` (port 8081),
`scheduler-service` (port 8082, Quartz jobs) and `notification-service` (port 8083, Lab 8). Details, run commands and Docker usage: `docs/multi-module-lab7.md`.
```bash
mvn verify                                  # builds and tests all modules (the Quartz integration test needs Docker, it is skipped without it)
mvn -pl .,common -DskipTests install        # once, so a service can be run on its own
mvn -pl quiz-service spring-boot:run
mvn -pl scheduler-service spring-boot:run
mvn -pl notification-service spring-boot:run
```
The old single-module commands from Lab 2 now apply per module: `mvn -pl quiz-service test`.

## Lab 8 — message brokers
`quiz-service` publishes a `QuizEvent` to RabbitMQ after a quiz is created, published or deleted; `notification-service`
consumes it and stores a notification (`GET /api/notifications`). No HTTP between the services. Details, scenario proofs,
theory and the known limits: `docs/messaging-lab8.md`.
```bash
docker compose --profile apps up --build    # broker, database, Keycloak and all three services in containers
mvn verify                                  # the integration tests need Docker (skipped without it)
```
