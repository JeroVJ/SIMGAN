# ===== Stage 1: build =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache dependencies
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Build
COPY src ./src
RUN mvn -B -q -DskipTests package

# ===== Stage 2: runtime =====
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the fat jar produced by Spring Boot
COPY --from=build /workspace/target/*.jar app.jar

# Container listens on the port Spring uses
ENV SERVER_PORT=8080
EXPOSE 8080

# JVM flags suited for small containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
