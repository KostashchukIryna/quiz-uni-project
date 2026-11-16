# QuizPlatform 

Kahoot-like quiz platform, which is a project for a Java course


## Lab 2 — unit tests
```bash
mvn verify        # runs tests, JaCoCo report (target/site/jacoco/index.html) and a 60% line-coverage gate
```
Tests live in `src/test/java` (JUnit 5 + Mockito + AssertJ, no Spring context). Main SUT: `QuizService`.
Mutation proof (AC4): `docs/mutation-evidence.md`. JaCoCo excludes generated `*MapperImpl` and `AppApplication`.

## Lab 7 — multi-module Maven
The repository is one Maven build with three modules: `common` (shared enum), `quiz-service` (port 8081) and
`scheduler-service` (port 8082, Quartz jobs). Details, run commands and Docker usage: `docs/multi-module-lab7.md`.
```bash
mvn verify                                  # builds and tests all modules (the Quartz integration test needs Docker, it is skipped without it)
mvn -pl .,common -DskipTests install        # once, so a service can be run on its own
mvn -pl quiz-service spring-boot:run
mvn -pl scheduler-service spring-boot:run
```
The old single-module commands from Lab 2 now apply per module: `mvn -pl quiz-service test`.
