# syntax=docker/dockerfile:1
# Build context: the repository root (the core library is compiled from source here).
#   docker build -f web/Dockerfile .

# Stage 1: compile the core rules library to an ES module package.
FROM eclipse-temurin:21-jdk AS core
WORKDIR /src
# Build scripts first so that the Gradle distribution, the dependencies and the Node/Yarn
# toolchain of Kotlin/JS are resolved in their own layer; the cache mount keeps the Gradle home
# between builds as well.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY core/build.gradle.kts ./core/
# settings.gradle.kts includes the engine and server projects; their build files are enough for configuration.
COPY engine/build.gradle.kts ./engine/
COPY server/build.gradle.kts ./server/
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    chmod +x gradlew && ./gradlew :core:dependencies --no-daemon -q > /dev/null
COPY core/src ./core/src
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew :core:jsNodeProductionLibraryDistribution --no-daemon -q

# Stage 2: install dependencies and build the Vite bundle.
FROM node:22-alpine AS web
WORKDIR /app/web
# The client references the core package by relative path, so recreate the same layout.
COPY --from=core /src/core/build/dist/js/productionLibrary /app/core/build/dist/js/productionLibrary
COPY web/package.json web/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund
COPY web/ ./
RUN npm run build

# Stage 3: serve the static bundle and proxy the API.
FROM nginx:alpine
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY deploy/security-headers.conf /etc/nginx/security-headers.conf
COPY --from=web /app/web/dist /usr/share/nginx/html
EXPOSE 80
