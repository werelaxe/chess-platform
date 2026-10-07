# Chess platform

Classic and quantum chess in the browser: a Kotlin Multiplatform rules library shared by the
server and the client, a Ktor API with PostgreSQL persistence, and a React web client.
Architecture and API: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Rules of both variants:
[docs/RULES.md](docs/RULES.md).

```
core/     Kotlin Multiplatform library (JVM + JS): rules, move application, serialization
engine/   the computer player (search and evaluation for both variants); used by the server only
server/   Ktor 3 API: auth, lobby, games, WebSocket events, computer opponent; Exposed + Flyway on PostgreSQL
web/      React 19 + TypeScript (Vite) client using the JS build of core
deploy/   nginx configuration used by the web image
```

The web UI is available in English (the default) and Russian; the switcher in the navigation
stores the choice in the browser and, for a signed-in user or guest, on the account
(`PATCH /api/auth/me`). The texts live in `web/src/i18n/<language>.json`. To add a language,
copy `en.json` to a new file, translate every string, register the file in
`web/src/i18n/locale.ts` (`LOCALES`) and `web/src/i18n/index.ts` (`resources`), and allow the code
on the server (`SUPPORTED_LOCALES` in `AuthService`); a test checks that all files carry exactly
the same keys.

## Running with docker compose

```bash
cp .env.example .env   # set POSTGRES_PASSWORD and JWT_SECRET; compose refuses to start without them
docker compose up --build
```

Caddy listens on ports 80 and 443 and forwards to nginx, which serves the client and proxies
`/api` (REST and WebSockets) to the API container; `web` starts once the API reports healthy.
Set `SITE_ADDRESS` in `.env` to the public hostname (for example `quantum-chess.fun`) and point
the domain's A record at the host: Caddy then obtains and renews the Let's Encrypt certificate
by itself and redirects HTTP to HTTPS for that hostname; `WWW_ADDRESS` (for example
`www.quantum-chess.fun`, with its own DNS record) redirects to it. Without `SITE_ADDRESS` the
stack serves plain HTTP on localhost. PostgreSQL data lives in the `db-data` volume, and the database is also
published on `127.0.0.1:5432` for local development.

## Deploying and operating the server

GitHub Actions do the deployments (`.github/workflows/deploy.yml`): the `api` and `web` images
are built in CI, pushed to GHCR (`ghcr.io/werelaxe/chess-platform-api|web`, tagged with the
commit SHA and `latest`) and the server is updated over SSH with
`docker-compose.prod.yml`, which runs those images instead of building. Trigger it from the
Actions tab ("Deploy", any branch or tag) or by pushing a tag such as `v1.2.3`. The workflow
needs the secret `DEPLOY_SSH_KEY` and the variables `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_PATH`
and `SITE_URL`. On the server itself only `.env`, the compose files and `deploy/Caddyfile` live
in `DEPLOY_PATH`. `ci.yml` runs all tests on every push and pull request.

To look at the running stack from your machine, point the Docker CLI at the server over SSH:

```bash
docker context create chess-vm --docker "host=ssh://werelaxe@130.193.48.247"
```

```bash
docker --context chess-vm compose ps
```

The same works for `logs -f api`, `exec db psql -U chess chess`, `stats` and so on (run compose
commands from the repository root so that the project name matches).

## Development

Requirements: JDK 17+ to run Gradle (a JDK 21 toolchain is provisioned automatically),
Node 22 (`web/.nvmrc`), Docker for the database. The `.env` file from the previous section is
needed here too: compose reads it even when only `db` is started.

```bash
# database on 127.0.0.1:5432 (credentials from .env)
docker compose up -d db

# API on http://localhost:8080 (defaults match the compose database; see docs/ARCHITECTURE.md 2.1)
JWT_SECRET=<at least 16 characters> DATABASE_PASSWORD=<POSTGRES_PASSWORD from .env> ./gradlew :server:run

# JS build of the rules library, consumed by the web client as a file: dependency
./gradlew :core:jsNodeProductionLibraryDistribution

# web client on http://localhost:5173 (proxies /api to :8080)
cd web && npm install && npm run dev
```

Tests:

```bash
./gradlew :core:jvmTest :core:jsNodeTest :engine:jvmTest :server:test
cd web && npm run typecheck && npm run test
```

The core test suite includes perft counts for the standard reference positions, so any change
to move generation that breaks a rule fails immediately.

## How a game works

The server stores only the move list of a game. Both the server and the client replay it with
the same library, so the client can show legal moves and apply its own moves instantly while the
server remains authoritative. The only random element, the outcome of a quantum observation, is
decided by the server and stored inside the move, which keeps every game fully reproducible.

## Credits

Chess piece images: "cburnett" set by Colin M.L. Burnett, CC BY-SA 3.0, via Wikimedia Commons.
