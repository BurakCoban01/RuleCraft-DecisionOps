# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:25.0.4_7-jdk-alpine-3.24 AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package && cp target/rulecraft-api-*.jar /workspace/rulecraft-api.jar

FROM eclipse-temurin:25.0.4_7-jre-alpine-3.24
RUN addgroup -S -g 10001 rulecraft && adduser -S -D -H -u 10001 -G rulecraft rulecraft
WORKDIR /opt/rulecraft
COPY --from=build --chown=rulecraft:rulecraft /workspace/rulecraft-api.jar app.jar
USER 10001:10001
EXPOSE 8080 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"
ENTRYPOINT ["java", "-jar", "/opt/rulecraft/app.jar"]
