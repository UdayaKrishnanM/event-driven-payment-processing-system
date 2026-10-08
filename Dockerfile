# syntax=docker/dockerfile:1.7
# One Dockerfile for all three services. The Maven build runs once (shared "build" stage, cached by BuildKit)
# and each service is a small final stage:  docker build --target payment-service .

############################ build ############################
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy POMs first so dependency downloads are cached between code changes
COPY pom.xml lombok.config ./
COPY common/pom.xml common/
COPY payment-service/pom.xml payment-service/
COPY ledger-service/pom.xml ledger-service/
COPY notification-service/pom.xml notification-service/

COPY common/src common/src
COPY payment-service/src payment-service/src
COPY ledger-service/src ledger-service/src
COPY notification-service/src notification-service/src

# Tests run in CI and locally with `mvn verify`; the image build only packages.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -q package -DskipTests -Djacoco.skip=true

############################ runtime base ############################
FROM eclipse-temurin:17-jre AS runtime
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --home /app app
WORKDIR /app
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

############################ services ############################
FROM runtime AS payment-service
COPY --from=build /workspace/payment-service/target/payment-service.jar /app/app.jar
EXPOSE 8081

FROM runtime AS ledger-service
COPY --from=build /workspace/ledger-service/target/ledger-service.jar /app/app.jar
EXPOSE 8082

FROM runtime AS notification-service
COPY --from=build /workspace/notification-service/target/notification-service.jar /app/app.jar
EXPOSE 8083
