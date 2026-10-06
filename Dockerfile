# ==============================================================================
# Zero-Knowledge Document Verification Platform
# Production Multi-Stage Dockerfile (Java 21 LTS)
# ==============================================================================

# --- Stage 1: Build Stage ---
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Install Node.js, npm, and build utilities required by frontend-maven-plugin
RUN apk add --no-cache bash nodejs npm

# Copy Maven wrapper and root POM
COPY mvnw ./
COPY mvnw.cmd ./
COPY .mvn ./.mvn
COPY pom.xml ./

# Copy module POMs
COPY zkdoc-core-crypto/pom.xml ./zkdoc-core-crypto/
COPY zkdoc-document/pom.xml ./zkdoc-document/
COPY zkdoc-credential/pom.xml ./zkdoc-credential/
COPY zkdoc-zkp/pom.xml ./zkdoc-zkp/
COPY zkdoc-keys/pom.xml ./zkdoc-keys/
COPY zkdoc-api/pom.xml ./zkdoc-api/

# Copy source code and frontend
COPY zkdoc-core-crypto/src ./zkdoc-core-crypto/src
COPY zkdoc-document/src ./zkdoc-document/src
COPY zkdoc-credential/src ./zkdoc-credential/src
COPY zkdoc-zkp/src ./zkdoc-zkp/src
COPY zkdoc-keys/src ./zkdoc-keys/src
COPY zkdoc-api/src ./zkdoc-api/src
COPY frontend ./frontend

# Package application without running full test suite during docker build
RUN chmod +x ./mvnw && ./mvnw clean package -DskipTests

# --- Stage 2: Runtime Stage ---
FROM eclipse-temurin:21-jre-alpine AS runtime

# Create unprivileged system group and user
RUN addgroup -S zkdoc && adduser -S zkdoc -G zkdoc

WORKDIR /app

# Copy executable jar from builder stage
COPY --from=builder --chown=zkdoc:zkdoc /build/zkdoc-api/target/zkdoc-api-*.jar /app/app.jar

# Switch to non-root user
USER zkdoc:zkdoc

EXPOSE 8080

# Configure JVM container ergonomics and entropy source
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app/app.jar"]
