# Build context is the repository root:  docker build -f quiz-service/Dockerfile -t quiz-service .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN mvn -B -q -pl quiz-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/quiz-service/target/quiz-service-*.jar app.jar
# the docker-compose support is for local development only
ENV SPRING_DOCKER_COMPOSE_ENABLED=false
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
