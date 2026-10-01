# Stage 1: build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src ./src
RUN ./gradlew --no-daemon bootJar

# Stage 2: runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 ottos
COPY --from=build /app/build/libs/ottos-backend-*.jar app.jar
USER ottos
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
