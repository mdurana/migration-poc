# Stage 1: Build the application
FROM eclipse-temurin:25-jdk-jammy AS builder
WORKDIR /workspace

# Copy the parent pom and the orchestrator module
COPY pom.xml .

# Copy the Maven wrapper scripts and configuration
COPY mvnw .
COPY .mvn ./.mvn

COPY orchestrator/pom.xml ./orchestrator/
COPY orchestrator/src ./orchestrator/src/

# Run the build from the root. Maven will handle the multi-module build.
RUN ./mvnw clean package -DskipTests

# Stage 2: Create the final image
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

# Copy the JAR from the orchestrator module's target directory
COPY --from=builder /workspace/orchestrator/target/orchestrator-0.0.1-SNAPSHOT.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]