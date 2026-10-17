# Build: compile and package the API (tests run in CI, not here).
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

# Run: a JRE and the jar, as a non-root user.
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 1001 cadence
COPY --from=build /app/target/*.jar app.jar
USER cadence
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
