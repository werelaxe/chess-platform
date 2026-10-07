# Chess platform architecture

The platform hosts two chess variants, classic and quantum, playable in a browser and (later)
from mobile clients. The rules live in one Kotlin Multiplatform library used by every component,
so a client can validate and apply moves instantly while the server stays authoritative.

```
chess-platform/
  core/      Kotlin Multiplatform library (JVM + JS): rules, move application, serialization
  engine/    Kotlin Multiplatform library (JVM): the computer player (search and evaluation)
  server/    Ktor HTTP + WebSocket API, PostgreSQL persistence (Exposed + Flyway), computer opponent
  web/       React + TypeScript client (Vite); consumes the JS build of core
  deploy/    nginx config and other deployment files
  docs/      this document and the rules text
  docker-compose.yml   db + api + web
```

## 1. Core library (`core`)

Package `ru.werelaxe.chess.core`. Pure Kotlin, no platform APIs. Published to the web client as
an npm package (`chess-platform-core`) built by `./gradlew :core:jsNodeProductionLibraryDistribution`
into `core/build/dist/js/productionLibrary`.

### 1.1 Model

| Type | Meaning |
|------|---------|
| `Color` | `WHITE`, `BLACK` |
| `PieceType` | `PAWN`, `KNIGHT`, `BISHOP`, `ROOK`, `QUEEN`, `KING` |
| `Piece(color, type)` | a piece |
| `Square(index)` | `index = rank * 8 + file`; a1 = 0, h1 = 7, a8 = 56, h8 = 63. Serialized as `"e4"` |
| `Move(from, to, promotion?)` | classical move; castling = king moves two files; en passant implied |
| `Board` | immutable classical position: 64 squares, side to move, castling rights, en passant square |
| `GameMove` | variant-independent move (see 1.3) |
| `GameStatus` | `Ongoing` or `Finished(winner?, reason)` |
| `BoardView` | rendering model shared by both variants (see 1.4) |
| `Game(kind)` | current state + move history, replayable from the history alone |

`Board.enPassant` is set only when an en passant capture is actually legal, so positions that
differ only by an unusable en passant square are equal (which is also what FIDE's repetition
rule requires). Equality of `Board` is the equivalence relation used to merge quantum universes.

### 1.2 Variants

`Variant<S>` (abstract) with implementations `ClassicVariant` and `QuantumVariant`;
`Variants.of(kind)` resolves by `GameKind` (`CLASSIC`, `QUANTUM`). `Game` wraps a variant
and hides the state type.

Classic rules: full FIDE movement including castling (empty and unattacked path), en passant,
promotion with piece choice; checkmate, stalemate, insufficient material, fifty-move rule
(automatic at 100 plies) and threefold repetition (automatic) end the game.

Quantum rules (see `docs/RULES.md` for the player-facing text):

* The state is a weighted multiset of classical universes `Map<Board, Long>`; probability of a
  universe = weight / total weight. Weights are divided by their GCD after every move, which is
  the "collapse by equivalence classes": identical universes merge, and the representation of a
  distribution is unique. The total weight is kept at or below 2^50 so that a split can never
  overflow: above that bound universes with probability below 2^-40 are dropped and the
  remaining weights are scaled down with rounding (relative error below 2^-49).
* `Normal(from, to, promotion?)`: applied in every universe where it is a legal classical move;
  universes where it is illegal (piece absent, blocked, pinned, ...) pass the turn unchanged.
  Legal overall if legal in at least one universe.
* `Split(from, first, second, promotion?)`: in each universe, if both targets are legal the
  universe is duplicated with the piece going to `first` in one copy and `second` in the other
  (equal weights); if only one target is legal the piece goes there with double weight; if
  neither, the universe passes with double weight. `second == from` means "stay". Requires
  `first != from`, `first != second`, `first` legal somewhere and `second` legal somewhere (or
  stay). `promotion` applies to whichever target promotes a pawn.
* `Observe(square, outcome)`: measurement. The authoritative side samples `outcome` from the
  square's distribution (`Game.resolve`, which ignores any outcome a client may have sent),
  then all universes inconsistent with the outcome are removed and the turn passes. Legal only
  when the square has at least two possible contents.
* Check is enforced per universe by classical rules. Because a move may not apply in some
  universes, a king can be left in check there and captured later. A side without a king in a
  universe simply plays on there.
* End of game: a player whose king is gone from **all** universes loses (`KING_CAPTURED`).
  A player with no legal move in any universe loses if in check in at least one of them
  (`CHECKMATE`), otherwise it is a draw (`STALEMATE`). One hundred plies (fifty moves by each
  side) without a capture or pawn move in any universe, or the same distribution occurring
  three times, is a draw.
* The move list is the only persistent form of a game; replaying it reproduces the exact
  distribution because observation outcomes are stored inside `Observe` moves.

### 1.3 JSON formats (produced by `ChessJson`)

```jsonc
// GameMove — polymorphic on "type"
{"type":"normal","from":"e2","to":"e4"}
{"type":"normal","from":"e7","to":"e8","promotion":"QUEEN"}
{"type":"split","from":"g1","first":"f3","second":"h3"}
{"type":"split","from":"e2","first":"e4","second":"e2"}          // second == from: stay
{"type":"observe","square":"e4"}                                  // proposed by a client
{"type":"observe","square":"e4","outcome":{"piece":{"color":"WHITE","type":"PAWN"}}}
{"type":"observe","square":"e4","outcome":{"piece":null}}         // observed empty

// GameStatus
{"type":"ongoing"}
{"type":"finished","winner":"WHITE","reason":"CHECKMATE"}         // winner null = draw
// reasons: CHECKMATE, STALEMATE, INSUFFICIENT_MATERIAL, FIFTY_MOVE_RULE, THREEFOLD_REPETITION,
//          KING_CAPTURED, RESIGNATION, DRAW_AGREEMENT, ABANDONMENT

// BoardView
{
  "sideToMove": "WHITE",
  "cells": [ {"square":"a1","entries":[{"piece":{"color":"WHITE","type":"ROOK"},"probability":1.0}]}, ... 64 ],
  "universeCount": 1,
  "checkProbability": 0.0,
  "lastMove": {"type":"normal","from":"e2","to":"e4"}
}
```

A cell with an empty `entries` list is certainly empty. An entry with `"piece": null` is the
probability of the square being empty (present only when 0 < p < 1). Entries are sorted by
decreasing probability.

### 1.4 JavaScript facade (`jsMain`, exported to TypeScript)

```ts
class JsGame {
  constructor(kind: "CLASSIC" | "QUANTUM");
  kind: string;
  sideToMove(): "WHITE" | "BLACK";
  moveCount(): number;
  universeCount(): number;
  isOver(): boolean;
  statusJson(): string;      // GameStatus JSON
  viewJson(): string;        // BoardView JSON
  historyJson(): string;     // GameMove[] JSON
  legalTargets(from: number): Int32Array;                  // square indices
  splitSecondTargets(from: number, first: number): Int32Array;
  requiresPromotion(from: number, to: number): boolean;
  canObserve(square: number): boolean;
  isLegalJson(moveJson: string): boolean;
  applyJson(moveJson: string): void;                       // throws Error on illegal move
}
function replayGame(kind: string, movesJson: string): JsGame;
function squareName(index: number): string;
function squareIndex(name: string): number;
```

Clients never receive state JSON; they receive the move list and replay it.

## 2. Server (`server`)

Ktor 3 on Netty. Package `ru.werelaxe.chess.server`. Stateless except for an in-memory cache of
replayed games and the WebSocket subscriber registry; PostgreSQL is the source of truth.

### 2.1 Configuration (environment variables)

| Variable | Default | Purpose |
|----------|---------|---------|
| `PORT` | `8080` | HTTP port |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/chess` | JDBC URL |
| `DATABASE_USER` / `DATABASE_PASSWORD` | `chess` / `chess` | DB credentials |
| `JWT_SECRET` | none: required (at least 16 characters), the server refuses to start without it | HS256 key |
| `JWT_TTL_DAYS` | `30` | token lifetime |
| `CORS_ORIGINS` | `http://localhost:5173` | comma-separated allowed origins (dev only; in production nginx serves both) |

Flyway migrations in `server/src/main/resources/db/migration` run at startup.

### 2.2 Database schema

```sql
users(id BIGSERIAL PK, username VARCHAR(20), username_lower VARCHAR(20) UNIQUE,
      password_hash TEXT NULL, is_guest BOOLEAN, is_bot BOOLEAN, locale VARCHAR(8) NULL,
      created_at TIMESTAMPTZ)
games(id VARCHAR(16) PK, kind VARCHAR(16), visibility VARCHAR(16), status VARCHAR(16),
      creator_id BIGINT FK users, white_id BIGINT FK users NULL, black_id BIGINT FK users NULL,
      move_count INT, result_winner VARCHAR(8) NULL, result_reason VARCHAR(32) NULL,
      draw_offered_by VARCHAR(8) NULL, bot_level VARCHAR(8) NULL,
      created_at, updated_at, finished_at TIMESTAMPTZ NULL)
moves(game_id FK games ON DELETE CASCADE, ply INT, move JSONB, created_at TIMESTAMPTZ, PK(game_id, ply))
```

`games.status` is `WAITING` (one player, open for joining), `ACTIVE` or `FINISHED`.
Game ids are 12 random base62 characters; a path id of any other shape is answered with 404
without touching the database. Usernames: 3–20 characters `[A-Za-z0-9_]`, unique
case-insensitively; passwords 8–72 characters, stored as bcrypt (cost 12). `users.locale` is the
UI language the user picked through `PATCH /api/auth/me` (`en` or `ru`); it is null until they
pick one, and the client then falls back to its default language, English.

Guests are ordinary `users` rows named `guest-NNNNNN` (six random digits 100000–999999, drawn
again on collision) with `is_guest = true` and no `password_hash`; the token issued at creation
is the only way to act as one. The prefix `guest-` is reserved (case-insensitively): registration
rejects such names with 400 `validation`, and logging in with one fails with 401
`invalid_credentials` because the row has no password.

The computer is the `users` row named `computer` with `is_bot = true` and no password, created
at startup when missing (the server refuses to start if a regular account holds the name). It
cannot log in, and registering the name fails with 409 `username_taken` like any taken name.
`games.bot_level` (`EASY`, `MEDIUM`, `HARD`) marks a game against the computer; in such a game
the creator is the human and the computer holds the other seat.

### 2.3 HTTP API

All endpoints under `/api`, JSON bodies, `Authorization: Bearer <jwt>` where required.
Errors: `{"error":"<code>","message":"<human readable>"}` with a matching HTTP status
(400 validation / illegal move, 401 unauthenticated, 403 forbidden, 404 not found, 409 conflict,
429 rate limited). `/api/auth/*` is limited to 20 requests per minute per client IP; above that
the server answers 429 `rate_limited`.

```
POST /api/auth/register   {username, password}        -> 201 {token, user}     409 username_taken;
                                                                                 400 validation for guest-* names
POST /api/auth/login      {username, password}        -> 200 {token, user}     401 on bad credentials or a guest name
POST /api/auth/guest      (no body)                    -> 201 {token, user}     user.guest = true
GET  /api/auth/me                                      -> 200 UserProfile      (auth)
PATCH /api/auth/me        {locale: "en"|"ru"|null}     -> 200 UserProfile      (auth, guests too; 400 validation for
                                                                                 any other value or a missing field)

POST /api/games           {kind, visibility, color, opponent, level} -> 201 GameDto  (auth)
                            kind: CLASSIC|QUANTUM, visibility: PUBLIC|PRIVATE, color: WHITE|BLACK|RANDOM,
                            opponent: HUMAN (default) | COMPUTER, level: EASY|MEDIUM|HARD
                            (required with COMPUTER: 400 validation without it; ignored with HUMAN)
GET  /api/games?filter=open|active|finished&kind=&limit=&offset= -> {games:[GameSummary]}  (public games only)
GET  /api/games/mine?limit=&offset=                    -> {games:[GameSummary]} (auth; newest first)
GET  /api/games/{id}                                   -> GameDto              (anyone with the id)
POST /api/games/{id}/join                              -> 200 GameDto          (auth; 409 game_full unless WAITING,
                                                                                 hence always for computer games;
                                                                                 400 if own game)
POST /api/games/{id}/moves {move: GameMove}            -> 200 {ply, move, status} (auth; player on turn)
                            400 illegal move, 403 not a player / not your turn,
                            409 game_not_started (WAITING) / game_finished
POST /api/games/{id}/resign                            -> 200 GameDto          (auth; player of an ACTIVE game,
                                                                                 or creator of a WAITING game)
POST /api/games/{id}/draw  {action: OFFER|ACCEPT|DECLINE|WITHDRAW} -> 200 GameDto (auth; player; ACTIVE only;
                                                                                 409 draw_not_available
                                                                                 in computer games)
GET  /api/health                                       -> 200 {status:"ok"}
```

In both listings `limit` defaults to 50 and is clamped to 1..200; `offset` defaults to 0.

A guest token has the same lifetime as a registered user's and grants the same rights: guests
create, join and play games exactly like registered users and appear in `GameSummary` with
`"guest": true`.

The `/api/auth/*` endpoints describe the caller's own account as a `UserProfile` (the `user` of
the register, login and guest responses, and the body of `/api/auth/me`): the `UserRef` fields
plus `locale`, the UI language chosen through `PATCH /api/auth/me`. Only `en` and `ru` are
accepted; `null` clears the choice. The field is always present in the profile and null until
set. Other players only ever appear as `UserRef` inside game DTOs, so the locale is never shown
to anyone else. Guests set it like registered users; it lives with the account, so a registered
user gets it back on every login.

Draw offers: `OFFER` records the color (409 `draw_pending` while one is open); `ACCEPT` by the
opponent finishes the game with `DRAW_AGREEMENT`; `DECLINE` (opponent) and `WITHDRAW` (offerer)
clear it, and so does a move by the opponent, whereas the offerer's own move keeps the offer
open. `ACCEPT`/`DECLINE`/`WITHDRAW` without a matching offer: 409 `no_draw_offer`.

Resigning an `ACTIVE` game finishes it with `RESIGNATION` in favour of the opponent. A `WAITING`
game is cancelled by its creator through the same endpoint: the game is deleted, and the
response (and the `game` event) carry the DTO with `status: "FINISHED"` and
`result: {"winner": null, "reason": "ABANDONMENT"}`. Resigning a `FINISHED` game is 409
`game_finished`.

```jsonc
UserRef     {"id": 1, "username": "alice", "guest": false, "bot": false}   // "guest" and "bot" are omitted when false
UserProfile UserRef + "locale": "en"|"ru"|null                             // the caller's own account; "locale" is
                                                                           // always present, null until set
GameSummary {"id":"aZ3kq9...","kind":"QUANTUM","visibility":"PUBLIC","status":"ACTIVE",
             "white":UserRef|null,"black":UserRef|null,"creator":UserRef,
             "moveCount":12,"result":{"winner":"WHITE"|"BLACK"|null,"reason":"..."}|null,
             "drawOfferedBy":"WHITE"|"BLACK"|null,"botLevel":"EASY"|"MEDIUM"|"HARD"|null,
             "createdAt":"2026-10-07T10:00:00Z","updatedAt":"..."}
GameDto     GameSummary + "moves": [GameMove, ...]
```

### 2.4 Move handling

1. Load the `Game` for the id (cache keyed by id, populated by replaying `moves` from the DB);
   serialize access per game with a mutex.
2. Check the caller is the player whose color is on turn and the game is `ACTIVE`.
3. `move = game.resolve(move, random)` (fills observation outcomes), reject if `!game.isLegal(move)`.
4. `game.apply(move)`; insert the move row with `ply = previous move count`; update
   `move_count` and `updated_at`; clear `draw_offered_by` unless the mover is the offerer;
   if `game.status()` is finished, set `status = FINISHED`, `result_*`, `finished_at`.
5. Broadcast the event to WebSocket subscribers of the game.

### 2.5 WebSocket

`GET /api/games/{id}/ws`. The connection is anonymous: it only streams events, so players and
spectators connect the same way, and every state change goes through the REST API. Unknown
ids are refused with a close frame. Server → client events (JSON, `type` discriminator):

```jsonc
{"type":"move","ply":12,"move":GameMove,"status":GameStatus}
{"type":"game","game":GameDto}      // sent on connect and whenever players/status/draw offer change
{"type":"pong"}
```

Client → server: `{"type":"ping"}` every 30 s. The client applies a `move` event locally when
`ply == localMoveCount`; otherwise it refetches the game and replays.

### 2.6 Computer opponent

A game created with `opponent: COMPUTER` is `ACTIVE` at once: the human takes the colour they
chose (`RANDOM` is honoured) and the `computer` account takes the other seat. Its visibility is
forced to `PRIVATE`: it never appears in the public listing but is reachable by its link and
listed in `/api/games/mine`. `GameSummary.botLevel` carries the level, and the computer's
`UserRef` has `"bot": true`. Draw offers are not available (409 `draw_not_available`); resigning
works as usual, and joining is refused like for any `ACTIVE` game.

The computer plays through `BotPlayer` using the engine in `engine/`
(`ru.werelaxe.chess.engine.ChessEngine`, levels `EASY`/`MEDIUM`/`HARD` with thinking budgets of
0.7/1.5/3.5 s). The service wakes it whenever it may be the computer's turn: right after
creation when it plays white, after every move, and, as self-healing after a restart, whenever
the game is fetched (`GET /api/games/{id}`) or a WebSocket connects to it. A wake-up checks the
move count cheaply and, if it is the computer's turn, takes an immutable snapshot of the
position under the game's lock and searches outside it on a dispatcher limited to two threads;
a game is never searched twice at the same time, and a wake-up that arrives during a search is
honoured afterwards. The chosen move goes through the normal move pipeline (`GameService.move`
with the computer's principal), so observations get their outcome from the server and clients
receive the usual `move` events. A reply to a human move is delayed so that at least 700 ms pass
after the move (the search time counts towards it). A rejected move (the human resigned
meanwhile, or the engine erred) is logged and followed by a fresh look at the game, a few times
at most; any other failure is logged and left for the next wake-up.

## 3. Web client (`web`)

Vite + React 19 + TypeScript, router and a small store (zustand). The JS core is a dependency
(`"chess-platform-core": "file:../core/build/dist/js/productionLibrary"`), wrapped by
`src/core/game.ts` which parses the JSON strings into typed objects (`src/api/types.ts` mirrors
section 1.3 and 2.3).

Pages: Home (lobby: open public games to join, active public games to watch, create game),
Login, Register, My games, Game, Rules (classic and quantum), About (credits: cburnett pieces,
CC BY-SA 3.0).

Game page behaviour:

* The board is rendered as an 8x8 grid of SVG pieces (`web/public/pieces/Chess_<p><l|d>t45.svg`,
  the cburnett set), white at the bottom for white/spectators, flipped for black.
* Moves: click a piece, click a target. Promotion opens a piece picker. Quantum games add a
  **Split** mode (first target, then second target or the origin square to stay) and an
  **Observe** mode (click a square with a non-trivial distribution). Normal and split moves
  are applied locally first and then sent; observations are only applied when the server's
  event arrives (the outcome is random).
* Probabilities: a certain piece is drawn normally. A piece with probability p < 1 is drawn
  with opacity `0.4 + 0.6 p`, a percentage badge in the corner of the cell and a thin ring
  around the piece filled proportionally to p. A cell with several possible pieces shows up
  to four of them as a 2x2 mini grid with their percentages. Hovering (or tapping) a cell shows
  the full distribution, including "empty", in a side panel.
* Highlights: selected square, legal targets (dot; ring on captures), chosen first split
  target, last move, and the king's cell tinted by `checkProbability`.
* Game header: players, side to move, status, universe count (quantum), resign / draw buttons,
  move list, share link for private games.

## 4. Deployment

`docker-compose.yml` runs four services: `db` (postgres:17, named volume; published on
`127.0.0.1:5432` only, so that a server started from Gradle can use it), `api`
(`server/Dockerfile`: multi-stage Gradle build on Temurin 21, runs as an unprivileged user,
healthcheck on `/api/health`) and `web` (`web/Dockerfile`: builds the core JS library and the
Vite bundle, served by nginx which also proxies `/api` including WebSockets to `api`; starts
once `api` is healthy) and `caddy` (`caddy:2-alpine`, `deploy/Caddyfile`: the only service with
published ports, 80 and 443). Both Dockerfiles copy the build scripts before the sources and keep
the Gradle and npm caches in BuildKit cache mounts, so a source change does not download the
dependencies again. `POSTGRES_PASSWORD` and `JWT_SECRET` come from `.env` (`.env.example` lists
them); compose refuses to start when either is missing. Mobile clients will talk to the same
`/api` through Caddy and nginx.

nginx serves `index.html` with `Cache-Control: no-cache` and the hashed `/assets/` as
immutable, hides its version and adds `X-Content-Type-Options`, `X-Frame-Options` and
`Referrer-Policy` to every response (`deploy/security-headers.conf`). Because only Caddy reaches
nginx, nginx trusts the `X-Forwarded-For` header from the private network ranges and reports the
real client address in its access log and in `X-Real-IP` to the API (which rate limits by it).
Container logs go to Docker's json-file driver with rotation (`docker compose logs <service>`).

Deployments are done by the GitHub Actions workflow `deploy.yml`: it builds both images, pushes
them to GHCR tagged with the commit SHA and `latest`, copies the compose files to the server and
runs `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d` there over SSH with
`IMAGE_TAG` set to the SHA, then checks `/api/health`. Rolling back is the same workflow run on
an older commit. `ci.yml` runs the test suites on every push and pull request.

TLS is terminated by Caddy. With `SITE_ADDRESS` set to the public hostname it obtains a
Let's Encrypt certificate (ACME over HTTP, so the domain's A record must point at the host and
port 80 must be reachable), renews it automatically, redirects HTTP to HTTPS for that hostname
and adds `Strict-Transport-Security`; `WWW_ADDRESS` names an optional `www.` host that only
redirects to it. Certificates persist in the `caddy-data` volume. Requests
for any other hostname (such as the bare IP address) are served over plain HTTP, and without
`SITE_ADDRESS` the stack serves plain HTTP on localhost for development.
