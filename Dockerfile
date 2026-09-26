# --- Stage 1: Build ---
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy pom
COPY pom.xml ./

# Increase network timeout for Maven
ENV MAVEN_OPTS="-Dhttp.keepAlive=false -Dmaven.wagon.http.pool=false -Dmaven.wagon.http.retryHandler.count=3"

# Cache dependencies
RUN mvn dependency:go-offline

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Security: Run as non-root user
RUN addgroup -S zenith && adduser -S zenith -G zenith
USER zenith

# Copy the jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Optimization: JVM flags for container awareness
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
