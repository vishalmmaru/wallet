# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copy Gradle wrapper and build files first, for better layer caching
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
# If you use build.gradle.kts / settings.gradle.kts instead, copy those too:
# COPY build.gradle.kts settings.gradle.kts ./

RUN chmod +x gradlew

# Pre-download dependencies (cached as long as build files don't change)
RUN ./gradlew dependencies --no-daemon || true

# Now copy the actual source and build
COPY src src

RUN ./gradlew clean bootJar --no-daemon -x test

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre

WORKDIR /app

# Run as non-root
RUN useradd -m spring
USER spring

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]