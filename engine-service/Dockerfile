# syntax=docker/dockerfile:1
# Build context is the repository root (the service depends on the core and engine libraries).
#   docker build -f engine-service/Dockerfile .

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# Build scripts first: the Gradle distribution and the dependencies are resolved in their own
# layer, so editing sources does not download them again. The cache mount keeps the Gradle home
# between builds as well.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY core/build.gradle.kts ./core/
COPY engine/build.gradle.kts ./engine/
COPY engine-service/build.gradle.kts ./engine-service/
# settings.gradle.kts includes the server project; its build file is enough for configuration.
COPY server/build.gradle.kts ./server/
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    chmod +x gradlew && ./gradlew :engine-service:dependencies --no-daemon -q > /dev/null
COPY core/src ./core/src
COPY engine/src ./engine/src
COPY engine-service/src ./engine-service/src
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew :engine-service:installDist --no-daemon -q

FROM eclipse-temurin:21-jre
RUN useradd --uid 10001 --user-group --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
# Owned by root and read-only for the service user; the service writes nothing to disk.
COPY --from=build /src/engine-service/build/install/engine-service /app
ENV PORT=8081
EXPOSE 8081
USER app
# GET /health answers 200 with the thread and queue counters; the compose healthcheck uses it.
ENTRYPOINT ["/app/bin/engine-service"]
