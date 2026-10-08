# syntax=docker/dockerfile:1

# ---------- build stage ----------
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /workspace

# Dependency layer: changes rarely, so it is cached independently of the sources.
COPY .mvn .mvn
COPY mvnw pom.xml ./
# A checkout from Windows may have CRLF endings or a lost executable bit on mvnw.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# Tests run in `./mvnw verify` and CI, not here: the Testcontainers suite needs a Docker socket.
RUN ./mvnw -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre-alpine
LABEL org.opencontainers.image.title="secure-statement-delivery" \
      org.opencontainers.image.version="1.0.4"
RUN addgroup -S app && adduser -S -u 10001 -G app app \
 && mkdir -p /data/statements /data/db && chown -R app:app /data
WORKDIR /app

# Application files stay owned by root, so the runtime user can't modify them; only /data is writable.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER 10001
VOLUME /data
EXPOSE 8080

# Defaults make a bare `docker run` work: file H2 + filesystem storage, dev token issuer, demo data.
ENV SPRING_PROFILES_ACTIVE=h2,dev,demo \
    APP_DATA_DIR=/data \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
