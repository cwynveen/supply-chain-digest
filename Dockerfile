# syntax=docker/dockerfile:1

# Build stage: compile with the full JDK. No third-party dependencies, so no Maven/Gradle needed.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY src ./src
RUN javac --release 21 -d out $(find src -name '*.java') \
 && jar --create --file app.jar --main-class digest.App -C out .

# Runtime stage: JRE only (~half the size of the JDK image).
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/app.jar ./app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
