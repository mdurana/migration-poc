# Stage 1: Build the application
# UPDATED to Java 25 JDK
FROM eclipse-temurin:25-jdk-jammy as builder
WORKDIR /workspace
COPY pom.xml .
COPY orchestrator/src ./orchestrator/src
RUN ./mvnw clean package -DskipTests

# Stage 2: Create the final image
# UPDATED to Java 25 JRE
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
COPY --from=builder /workspace/target/migration-poc-0.0.1-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]