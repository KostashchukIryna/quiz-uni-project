# Build context is the repository root:  docker build -f scheduler-service/Dockerfile -t scheduler-service .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN mvn -B -q -pl scheduler-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/scheduler-service/target/scheduler-service-*.jar app.jar
# the docker-compose support is for local development only
ENV SPRING_DOCKER_COMPOSE_ENABLED=false
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
