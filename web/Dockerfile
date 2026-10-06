# Build context: the repository root (the core library is compiled from source here).
#   docker build -f web/Dockerfile .

# Stage 1: compile the core rules library to an ES module package.
FROM eclipse-temurin:21-jdk AS core
WORKDIR /src
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY core ./core
# settings.gradle.kts includes the server project; its build file is enough for configuration.
COPY server/build.gradle.kts ./server/build.gradle.kts
RUN chmod +x gradlew && ./gradlew :core:jsNodeProductionLibraryDistribution --no-daemon -q

# Stage 2: install dependencies and build the Vite bundle.
FROM node:22-alpine AS web
WORKDIR /app/web
# The client references the core package by relative path, so recreate the same layout.
COPY --from=core /src/core/build/dist/js/productionLibrary /app/core/build/dist/js/productionLibrary
COPY web/package.json web/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY web/ ./
RUN npm run build

# Stage 3: serve the static bundle and proxy the API.
FROM nginx:alpine
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=web /app/web/dist /usr/share/nginx/html
EXPOSE 80
