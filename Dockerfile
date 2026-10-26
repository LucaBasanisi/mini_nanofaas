# Stage 1: Build
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# Copia i file di configurazione Gradle e il wrapper per sfruttare il caching dei layer Docker
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

# Scarica le dipendenze in anticipo
RUN ./gradlew dependencies --no-daemon || true

# Copia i sorgenti ed esegui la build saltando i test (già verificati in CI)
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Runtime minimale
FROM eclipse-temurin:25-jre
WORKDIR /app

# Installa la CLI di Docker per consentire a SwarmProvider di eseguire comandi sullo Swarm
RUN apt-get update && \
    apt-get install -y --no-install-recommends docker.io curl && \
    rm -rf /var/lib/apt/lists/*

# Copia il JAR compilato dallo stage precedente
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
