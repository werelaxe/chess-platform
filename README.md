# Chess platform

Classic and quantum chess in the browser: a Kotlin Multiplatform rules library shared by the
server and the client, a Ktor API with PostgreSQL persistence, and a React web client.
Architecture and API: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Rules of both variants:
[docs/RULES.md](docs/RULES.md).

```
core/     Kotlin Multiplatform library (JVM + JS): rules, move application, serialization
engine/   the computer player (search and evaluation for both variants)
engine-service/  stateless HTTP service around the engine; the API asks it for computer moves
server/   Ktor 3 API: auth, lobby, games, WebSocket events, computer opponent; Exposed + Flyway on PostgreSQL
web/      React 19 + TypeScript (Vite) client using the JS build of core
admin-cli/  command line tool for the admin API: bots and runtime settings
deploy/   nginx configuration of the web image, Caddy for docker compose, Kubernetes manifests (k8s/)
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

Production runs on Kubernetes, described by `deploy/k8s`: PostgreSQL as a StatefulSet with a
persistent volume, the `engine`, `api` and `web` Deployments and an Ingress for
`quantum-chess.fun`. The cluster's Traefik routes it to `web`, redirects HTTP to HTTPS and adds
`Strict-Transport-Security`; cert-manager obtains and renews the certificate (ClusterIssuer
`letsencrypt-prod`). Before the first deployment, `deploy/k8s/create-secret.sh` creates the
Secret with a random database password and JWT key.

GitHub Actions do the deployments (`.github/workflows/deploy.yml`): the `api`, `engine` and
`web` images are built in CI, pushed to GHCR (`ghcr.io/werelaxe/chess-platform-api|engine|web`,
tagged with the commit SHA and `latest`) and rolled out with `deploy/k8s/deploy.sh <SHA>`, after
which the workflow checks `/api/health`. Trigger it from the Actions tab ("Deploy" on `master`;
runs on other branches are skipped). The workflow needs the variable `SITE_URL` and the secret
`KUBE_CONFIG`, the kubeconfig of a service account that may only change the `chess` namespace;
`deploy/k8s/ci/kubeconfig.sh`, run with an administrator's kubeconfig, creates the account and
prints it:

```bash
deploy/k8s/ci/kubeconfig.sh | gh secret set KUBE_CONFIG --repo werelaxe/chess-platform
```

The manifests carry no image tag, so that applying them never rolls the app back: `deploy.sh`
pins the images to the tag it is given and, without one, to the version that runs now, which is
how a change to the manifests alone is applied. Rolling back is `deploy.sh <older SHA>` or the
workflow run on an older commit. `ci.yml` runs all tests on every push and pull request.

The computer's moves are searched by the `engine` service (`engine-service/`), the only
CPU-heavy part of the stack: the API asks it over HTTP (`ENGINE_URL`) and, for a game with a
person in it, falls back to its own in-process engine while the service is unavailable, so the
engine can be restarted or redeployed without stalling anybody's game; a game between two bots
simply waits for the service. The CPU limit of its container (`deploy/k8s/engine.yaml`;
`ENGINE_CPUS` in `.env` under docker compose) caps the cores one engine may use, and
`ENGINE_PARALLELISM` the number of concurrent searches (default: the cores it sees); requests
beyond that wait in a bounded queue with a shrinking thinking budget and are refused once it is
full, which the API retries. Moves for people are served before those of bot-versus-bot games,
and half of the queue is kept for them. A quantum position of more than `ENGINE_MAX_UNIVERSES`
universes (default 4096) is searched on its most probable universes only, so that one fog
position cannot exhaust the engine's memory (the API's own fallback engine does the same,
`BOT_MAX_UNIVERSES`). In the cluster a HorizontalPodAutoscaler in
`engine.yaml` adds a second engine pod while the CPU use stays above 75 %; to add capacity
beyond that, raise its `maxReplicas` (under docker compose: `docker compose up -d --scale
engine=2`), and the API's requests spread over the pods through the service name.

To look at the running stack, use `kubectl` with the cluster's kubeconfig:

```bash
kubectl -n chess get pods
```

The same goes for `logs -f deployment/api`, `exec -it db-0 -- psql -U chess chess`, `top pods`
and so on.

## Administration

The operational settings (whether the matchmaker runs, how many open games the bots keep in
the lobby, the pauses before a bot moves, the number of games with people above which the
bots stop playing each other, how long an untouched game lives, and so on) live in the
`settings` table of the database and apply without a restart; the environment variables of
docs/ARCHITECTURE.md 2.1 only provide their defaults. They are read and changed through the
admin API (section 2.11), which the API server enables when `ADMIN_TOKEN` (at least 16
characters) is set in its environment; every request must carry the token in `X-Admin-Token`.
The `admin-cli` module wraps the API as the `chess-admin` command:

```bash
./gradlew :admin-cli:installDist
export CHESS_API_URL=https://quantum-chess.fun     # default: http://localhost:8080
export CHESS_ADMIN_TOKEN=<the server's ADMIN_TOKEN>
admin-cli/build/install/admin-cli/bin/chess-admin overview
```

In the Kubernetes deployment the token is the `admin-token` key of the `chess` secret
(`deploy/k8s/create-secret.sh` creates it for new clusters; for an existing one patch the secret
and restart the API as the script's comment shows). Without the key the API runs normally with
the admin endpoints disabled.

```
chess-admin [--api URL] [--token TOKEN] [--json] <command>
  overview                                  users, games, bots and the current settings
  bots list                                 the roster with levels, ratings and game counts
  bots show <username>                      a bot with its ratings, active games and recent games
  bots spawn --username <name> --name "<display name>" --level NOVICE|EASY|MEDIUM|HARD|EXPERT
  bots set <username> [--level <level>] [--name "<display name>"]
  games list [--status WAITING|ACTIVE|FINISHED] [--limit <n>]   the games, newest first
  games abort <id>                          end a game without a winner; it stays in the records
  games drop <id>                           delete a game outright
  settings list                             every setting with its default and where the value comes from
  settings set <key> <value>
  settings reset <key>                      back to the default
  settings open-games <n>                   shortcut for bot.open_games_target
  settings live-games <n>                   shortcut for bot.live_games_target
  settings human-games-limit <n>            shortcut for bot.human_games_limit
  settings pauses [--reply <ms>] [--bots <ms>]   shortcuts for bot.reply_pause_millis and bot.vs_bot_pause_millis
```

`--help` on any command lists its options. `--api` defaults to `CHESS_API_URL` or
`http://localhost:8080`; `--token` defaults to `CHESS_ADMIN_TOKEN` and is required. The output
is a plain-text table; `--json` prints the API response as the server sent it. An error from
the API is shown as `<status> <code>: <message>` and the command exits with a non-zero code.
The production API is reachable through the site, so `--api https://quantum-chess.fun` works
from anywhere with the token.

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
./gradlew :core:jvmTest :core:jsNodeTest :engine:jvmTest :engine-service:test :server:test :admin-cli:test
cd web && npm run typecheck && npm run test
```

The core test suite includes perft counts for the standard reference positions, so any change
to move generation that breaks a rule fails immediately.

## How a game works

The server stores only the move list of a game. Both the server and the client replay it with
the same library, so the client can show legal moves and apply its own moves instantly while the
server remains authoritative. The only random element, the outcome of a quantum observation, is
decided by the server and stored inside the move, which keeps every game fully reproducible.
A game in which nobody has moved for a day (the `game.stale_hours` setting, 24 hours by
default) is finished by the server as abandoned: no winner and no rating change, exactly as if
an administrator had aborted it.

The computer player (`engine/`) is an alpha-beta search for classic chess and an expectimax
search over the universes for quantum chess, both with iterative deepening within the level's
time budget, repetition awareness and an endgame mop-up term; see docs/ARCHITECTURE.md
section 2.12.

## Ratings and bots

Registered players and bots have an Elo rating per variant (classic and quantum are rated
separately; guests play unrated). The leaderboard and player search live under `/players`,
and every player profile offers an invitation to a game. A roster of bots of different levels
accepts invitations immediately, keeps a couple of open games waiting in the lobby, joins open
games that have waited for a while and occasionally challenges recently active players; two
bots can play each other, and such games are public. When many people are playing at the
moment (games with a move in the last fifteen minutes, counted against the
`bot.human_games_limit` setting), the bots stop starting games with each other and the ones in
progress wait, so that the engine serves the people first. See docs/ARCHITECTURE.md sections
2.8–2.10.

## Credits

Chess piece images: "cburnett" set by Colin M.L. Burnett, CC BY-SA 3.0, via Wikimedia Commons.
