# ============================================================
# Take Off API — multi-stage Docker build (Spring Boot / JVM)
# Stage 1: build — Gradle wrapper builds the fat JAR
# Stage 2: runtime — Eclipse Temurin JRE, non-root user
# ============================================================

# ---------- Stage 1: build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy Gradle wrapper first (cached unless wrapper changes)
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
RUN chmod +x gradlew

# Copy build scripts before source (cache layer)
COPY build.gradle.kts settings.gradle.kts ./

# Download dependencies only (cache-friendly)
RUN ./gradlew dependencies --no-daemon -q

# Copy source and build
COPY src ./src
RUN ./gradlew bootJar --no-daemon -q

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Security: run as non-root
RUN groupadd -r takeoff && useradd -r -g takeoff takeoff

COPY --from=build /app/build/libs/*.jar app.jar

# RSA keys are mounted via Railway secret files or volume
RUN mkdir -p /app/keys && chown -R takeoff:takeoff /app

USER takeoff

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
