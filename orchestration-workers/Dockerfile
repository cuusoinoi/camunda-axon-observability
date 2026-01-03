# syntax=docker/dockerfile:1.6
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# BuildKit cache + retry (fixes flaky Maven Central downloads like zstd-jni)
# IMPORTANT: fail the image build if Maven fails all retries (do not "swallow" errors).
RUN --mount=type=cache,target=/root/.m2 \
    bash -lc 'set -euo pipefail; \
      ok=0; \
      for i in 1 2 3 4 5; do \
        if mvn -B -ntp -DskipTests package; then ok=1; break; fi; \
        echo "[mvn] retry $i/5"; sleep 5; \
      done; \
      test "$ok" = 1; \
      ls -lah target; \
      test -f target/orchestration-workers-1.0.0.jar'

FROM eclipse-temurin:17-jre
WORKDIR /app

# OpenTelemetry Java Agent (auto-instrumentation)
# Downloaded at image build time.
ARG OTEL_JAVA_AGENT_VERSION=2.10.0
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.10.0/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
COPY --from=build /app/target/orchestration-workers-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-javaagent:/otel/opentelemetry-javaagent.jar","-jar","/app/app.jar"]
