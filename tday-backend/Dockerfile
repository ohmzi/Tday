FROM gradle:8.12-jdk21 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon || true
COPY src ./src
RUN gradle buildFatJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S tday && adduser -S tday -G tday
WORKDIR /app
COPY --from=build /app/build/libs/tday-backend-all.jar app.jar
USER tday
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
