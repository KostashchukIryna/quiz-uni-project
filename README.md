# QuizPlatform 

Kahoot-like quiz platform, which is a project for a Java course


## Lab 2 — unit tests
```bash
mvn verify        # runs tests, JaCoCo report (target/site/jacoco/index.html) and a 60% line-coverage gate
```
Tests live in `src/test/java` (JUnit 5 + Mockito + AssertJ, no Spring context). Main SUT: `QuizService`.
Mutation proof (AC4): `docs/mutation-evidence.md`. JaCoCo excludes generated `*MapperImpl` and `AppApplication`.
