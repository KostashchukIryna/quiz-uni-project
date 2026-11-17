# Build context is the repository root:  docker build -f notification-service/Dockerfile -t notification-service .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN mvn -B -q -pl notification-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/notification-service/target/notification-service-*.jar app.jar
# the docker-compose support is for local development only
ENV SPRING_DOCKER_COMPOSE_ENABLED=false
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
