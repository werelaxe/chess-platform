FROM openjdk:8-alpine

COPY . /app
WORKDIR /app
RUN ["./gradlew", "jar"]
ENTRYPOINT ["java", "-jar", "./build/libs/chess-platform.jar"]
