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

Repetitions are counted over the whole game. `ClassicState` keeps a map of every position to
its count; `QuantumState` keeps a `PositionHistory`, a persistent chain of the distributions
reached so far (each state holds its predecessor's chain plus its own distribution, so
recording one copies nothing and a move costs the same in a 500-ply game as in a short one),
and exposes `repetitions`, how many times the current distribution has occurred, which the
engine also reads to avoid or seek a repetition.

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
{"type":"finished","winner":"WHITE","reason":"CHECKMATE"}         // winner null = draw, except with
                                                                  // ABANDONMENT: no result (section 2.8)
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
| `ENGINE_URL` | unset | base URL of the engine service (section 2.7); unset means the in-process engine |
| `ENGINE_RETRY_SECONDS` | `30` | how long to retry an overloaded engine service before falling back |
| `BOT_PARALLELISM` | `2` | concurrent in-process searches (fallback only) |
| `BOT_REMOTE_CONCURRENCY` | `64` | concurrent requests to the engine service |
| `GAME_IDLE_MINUTES` | `30` | idle time after which a cached game is dropped from memory |
| `ADMIN_TOKEN` | unset | enables the admin API (section 2.11) when set, at least 16 characters |
| `BOT_MATCHMAKING` | `true` | default of `bot.matchmaking` (section 2.11): whether the bots keep the lobby alive (section 2.10); `false` pauses the loop until the setting switches it on |
| `BOT_TICK_MIN_MINUTES` / `BOT_TICK_MAX_MINUTES` | `3` / `10` | pause between matchmaking ticks (the upper bound is raised to the lower one if set below it) |
| `BOT_OPEN_GAMES_TARGET` | `2` | public open games the bots keep in the lobby (0 switches this off) |
| `BOT_LIVE_GAMES_TARGET` | `2` | bot-versus-bot games the matchmaker keeps in progress (0 switches this off) |
| `BOT_HUMAN_GAMES_LIMIT` | `0` | default of `bot.human_games_limit` (section 2.11): games with a human played within the last fifteen minutes at or above which the bots start no games with each other and bot-versus-bot games wait (section 2.10); 0 switches this off |
| `BOT_JOIN_AFTER_MINUTES` | `8` | how long an open game waits before a bot may join it |
| `BOT_INVITATION_TTL_MINUTES` | `60` | lifetime of a bot's invitation to a human |
| `BOT_MAX_ACTIVE_GAMES` | `6` | games in progress per bot above which it sits matchmaking out |
| `OPEN_GAME_TTL_HOURS` | `24` | age after which a public open game nobody joined is deleted by the matchmaking loop, whoever created it (section 2.10) |
| `GAME_STALE_HOURS` | `24` | default of `game.stale_hours` (section 2.11): hours without a move after which a human on turn forfeits an `ACTIVE` game; a bot on turn: no winner, no rating change (section 2.3); 0 switches the sweep off |

Flyway migrations in `server/src/main/resources/db/migration` run at startup.

### 2.2 Database schema

```sql
users(id BIGSERIAL PK, username VARCHAR(20), username_lower VARCHAR(20) UNIQUE,
      password_hash TEXT NULL, is_guest BOOLEAN, is_bot BOOLEAN, locale VARCHAR(8) NULL,
      display_name VARCHAR(40) NULL, bot_level VARCHAR(8) NULL, last_seen_at TIMESTAMPTZ NULL,
      created_at TIMESTAMPTZ)
games(id VARCHAR(16) PK, kind VARCHAR(16), visibility VARCHAR(16), status VARCHAR(16),
      creator_id BIGINT FK users, white_id BIGINT FK users NULL, black_id BIGINT FK users NULL,
      invitee_id BIGINT FK users NULL, rated BOOLEAN,
      move_count INT, result_winner VARCHAR(8) NULL, result_reason VARCHAR(32) NULL,
      draw_offered_by VARCHAR(8) NULL, white_rating_before INT NULL, black_rating_before INT NULL,
      created_at, updated_at, last_move_at TIMESTAMPTZ NULL, finished_at TIMESTAMPTZ NULL)
moves(game_id FK games ON DELETE CASCADE, ply INT, move JSONB, created_at TIMESTAMPTZ, PK(game_id, ply))
ratings(user_id FK users, kind VARCHAR(16), rating INT, games INT, wins INT, draws INT, losses INT,
        peak INT, updated_at TIMESTAMPTZ, PK(user_id, kind))
rating_changes(id BIGSERIAL PK, game_id FK games ON DELETE CASCADE, user_id FK users, kind VARCHAR(16),
               rating_before INT, rating_after INT, created_at TIMESTAMPTZ)
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
`invalid_credentials` because the row has no password. The names `leaderboard`, `me` and `bots`
are reserved the same way (case-insensitively, 400 `validation`): they are literal path
segments under `/api/players` and `/api/auth`, so a profile so named could never be reached.

Bots are `users` rows with `is_bot = true`, a `bot_level` and no password (section 2.10);
they cannot log in and their names cannot be registered. `users.display_name` is the optional
name shown next to the username (1–40 characters, set through `PATCH /api/auth/me`);
`users.last_seen_at` is refreshed by authenticated requests at most once per five minutes.

Ratings live in `ratings(user_id FK users, kind, rating INT, games INT, wins INT, draws INT,
losses INT, peak INT, updated_at, PK(user_id, kind))` and every change is recorded in
`rating_changes(id BIGSERIAL PK, game_id FK games, user_id FK users, kind, rating_before INT,
rating_after INT, created_at)`. `games` gains `rated BOOLEAN NOT NULL DEFAULT FALSE`,
`invitee_id BIGINT FK users NULL` (the only account allowed to join a WAITING game created
by invitation) and `white_rating_before` / `black_rating_before INT NULL` (snapshots taken
when the game becomes ACTIVE). The legacy `games.bot_level` column is dropped; the level of a
bot game is the `bot_level` of the bot seat. `games.last_move_at` is when the game was last
played: the latest move, or the moment the game became ACTIVE before the first one (null while
WAITING); the stale sweep (section 2.3) counts idle time from it, not from `updated_at`, which
a draw offer touches as well.

### 2.3 HTTP API

All endpoints under `/api`, JSON bodies, `Authorization: Bearer <jwt>` where required.
Errors: `{"error":"<code>","message":"<human readable>"}` with a matching HTTP status
(400 validation / illegal move, 401 unauthenticated, 403 forbidden, 404 not found, 409 conflict,
429 rate limited). `/api/auth/*` is limited to 20 requests per minute per client IP; above that
the server answers 429 `rate_limited`.

```
POST /api/auth/register   {username, password}        -> 201 {token, user}     409 username_taken;
                                                                                 400 validation for guest-* and the
                                                                                 reserved names (section 2.2)
POST /api/auth/login      {username, password}        -> 200 {token, user}     401 on bad credentials or a guest name
POST /api/auth/guest      (no body)                    -> 201 {token, user}     user.guest = true
GET  /api/auth/me                                      -> 200 UserProfile      (auth)
PATCH /api/auth/me        {locale?, displayName?}      -> 200 UserProfile      (auth, guests too; a missing field is
                                                                                 left alone, null clears it; 400 validation
                                                                                 for a bad value or an empty patch)

POST /api/games           {kind, visibility, color, rated, opponent, opponentUsername} -> 201 GameDto  (auth)
                            kind: CLASSIC|QUANTUM, visibility: PUBLIC|PRIVATE, color: WHITE|BLACK|RANDOM,
                            opponent: OPEN (default; anyone may join) | USER (an invitation: only
                            opponentUsername may join; a bot accepts at once, see 2.10);
                            rated: boolean, default true for registered users, forced false for guests;
                            opponentUsername: required with USER (400 validation without it or for
                            oneself; 404 not_found for an unknown or guest name), ignored with OPEN
GET  /api/games?filter=open|active|finished&kind=&limit=&offset= -> {games:[GameSummary]}  (public games only)
GET  /api/games/mine?limit=&offset=                    -> {games:[GameSummary]} (auth; newest first)
GET  /api/games/{id}                                   -> GameDto              (anyone with the id)
POST /api/games/{id}/join                              -> 200 GameDto          (auth; 409 game_full unless WAITING,
                                                                                 hence always for bot games; 400 if
                                                                                 own game; 403 not_invited when the
                                                                                 invitation is for somebody else)
POST /api/games/{id}/moves {move: GameMove}            -> 200 {ply, move, status} (auth; player on turn)
                            400 illegal move, 403 not a player / not your turn,
                            409 game_not_started (WAITING) / game_finished
POST /api/games/{id}/resign                            -> 200 GameDto          (auth; player of an ACTIVE game,
                                                                                 or creator of a WAITING game)
POST /api/games/{id}/draw  {action: OFFER|ACCEPT|DECLINE|WITHDRAW} -> 200 GameDto (auth; player; ACTIVE only;
                                                                                 409 draw_not_available
                                                                                 in computer games)
POST /api/games/{id}/decline                           -> 204                   (auth; invitee of a WAITING invitation; deletes it
                                                                                 and publishes the game with "declined": true;
                                                                                 403 not_invited for anybody else, 409 game_full
                                                                                 once accepted)
GET  /api/invitations                                  -> {incoming:[GameSummary], outgoing:[GameSummary]} (auth; WAITING invitations)

GET  /api/players?query=&limit=&offset=                -> {players:[PlayerSummary]} (username or display name contains query, case-insensitive; no guests)
GET  /api/players/leaderboard?kind=CLASSIC|QUANTUM&limit=&offset= -> {entries:[LeaderboardEntry]} (rated players and bots, best first; no guests)
GET  /api/players/{username}                           -> PlayerProfile         (404 for unknown or guest usernames)
GET  /api/bots                                         -> {bots:[PlayerSummary]} (the roster, with levels and ratings)
GET  /api/health                                       -> 200 {status:"ok"}
```

In both listings `limit` defaults to 50 and is clamped to 1..200; `offset` defaults to 0.

A guest token has the same lifetime as a registered user's and grants the same rights: guests
create, join and play games exactly like registered users and appear in `GameSummary` with
`"guest": true`.

The `/api/auth/*` endpoints describe the caller's own account as a `UserProfile` (the `user` of
the register, login and guest responses, and the body of `/api/auth/me`): the `UserRef` fields
except the game-bound `rating`, plus `ratings` (as in a `PlayerSummary`, section 2.9) and
`locale`, the UI language chosen through `PATCH /api/auth/me`. Only `en` and `ru` are
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

Stale games: an `ACTIVE` game in which nobody has moved for longer than `game.stale_hours`
(section 2.11; default `GAME_STALE_HOURS`, 24; 0 switches this off) is forfeited by the player
on turn. `StaleGames` sweeps every ten minutes and finishes every such game, judged by the
record's `last_move_at` (the latest move, or the start of the game before the first one), as a
loss for the side to move (`GameService.forfeitStale`): `status: "FINISHED"`, `result:
{"winner": <the other colour>, "reason": "ABANDONMENT"}`, the rating update of a resignation
(section 2.8: none when the game is unrated or ended before both sides had moved), a pending
draw offer cleared, and the `game` event to its subscribers. Ending such a game without a
winner would let a player who is about to lose walk away and keep the rating; a guest counts
as a human here. Only moves count as playing: a draw offer touches `updated_at` but not
`last_move_at`, or the player on turn could keep a lost game alive forever by offering and
withdrawing a draw once a day. A game with a bot on turn is the exception: nobody walked away
from it (the engine was down, the bot's move failed, or the human-load gate of section 2.10
held it), so it ends like the admin abort (section 2.11), `winner: null` and no rating change
whatever the move count; this covers every game between two bots, and a human never gains
rating from a stalled bot. A sweep finishes at most 100 games, the longest idle first, and
leaves the rest to the next one; each candidate is checked again under the game's lock, so a
move that arrives between the listing and the finish keeps the game alive. Every game finished
this way is logged at INFO with who was on turn, whether it was rated and its idle time.
`WAITING` games are not swept here: public open games nobody joined expire through the
matchmaking loop (section 2.10).

```jsonc
UserRef     {"id": 1, "username": "alice", "name": "Alice", "guest": false, "bot": false, "botLevel": null, "rating": 1512}
            // "guest"/"bot" omitted when false; "name" null when unset; "rating" is the player's current rating
            // in the game's kind (null for guests, and null outside game DTOs: player listings carry the
            // ratings separately); "botLevel" set for bots only; "name", "botLevel" and "rating" are always present
UserProfile {"id", "username", "name", "guest", "bot", "botLevel", "locale": "en"|"ru"|null,
             "ratings": {"CLASSIC": RatingSummary|null, "QUANTUM": RatingSummary|null}}
            // the caller's own account; "locale" is always present, null until set
GameSummary {"id":"aZ3kq9...","kind":"QUANTUM","visibility":"PUBLIC","status":"ACTIVE","rated":true,
             "white":UserRef|null,"black":UserRef|null,"creator":UserRef,"invitee":UserRef|null,
             "moveCount":12,"result":{"winner":"WHITE"|"BLACK"|null,"reason":"..."}|null,
             "drawOfferedBy":"WHITE"|"BLACK"|null,"declined":true,
             "createdAt":"2026-10-07T10:00:00Z","updatedAt":"..."}
            // "invitee" is the invited player of an invitation (kept after it is accepted), null for an open
            // game; a bot's level is the "botLevel" of its UserRef. "declined" is present (true) only in the
            // DTO that reports an invitation its invitee declined (the `game` event and nothing else), so the
            // creator can tell it from their own cancellation, which looks the same otherwise (FINISHED by
            // ABANDONMENT); it is omitted everywhere else, like "guest" and "bot" when false
GameDto     GameSummary + "moves": [GameMove, ...]
            + "ratingChanges": {"white": {"before", "after"}, "black": {"before", "after"}} | null   (section 2.8)
```

### 2.4 Move handling

1. Load the `Game` for the id (cache keyed by id, populated by replaying `moves` from the DB);
   serialize access per game with a mutex.
2. Check the caller is the player whose color is on turn and the game is `ACTIVE`.
3. `move = game.resolve(move, random)` (fills observation outcomes), reject if `!game.isLegal(move)`.
4. `game.apply(move)`; insert the move row with `ply = previous move count`; update
   `move_count`, `updated_at` and `last_move_at`; clear `draw_offered_by` unless the mover is the offerer;
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

### 2.6 Playing against a bot (the mechanics of the bot player; the roster is in 2.10)

A game against a bot is an invitation to it (`opponent: USER` with the bot's username) and is
`ACTIVE` at once: the human takes the colour they chose (`RANDOM` is honoured) and the bot
takes the other seat. Its visibility is whatever was asked for, so a public bot game is listed
and watchable like any other, and it is rated by default like any other game. The bot's
`UserRef` has `"bot": true` and its `botLevel`. Draw offers are not available in any game with a
bot seat (409 `draw_not_available`); resigning works as usual, and joining is refused like for
any `ACTIVE` game.

Bots play through `BotPlayer` using the engine in `engine/`
(`ru.werelaxe.chess.engine.ChessEngine`, levels `NOVICE`/`EASY`/`MEDIUM`/`HARD`/`EXPERT` with
thinking budgets of 0.3/0.7/1.5/3.5/6 s; section 2.12 describes the search). The service wakes it whenever it may be a bot's turn:
right after creation when it plays white, after every move, when somebody joins its open game,
and, as self-healing after a restart or a failed search, whenever the game is fetched
(`GET /api/games/{id}`) or a WebSocket connects to it, and for every `ACTIVE` game with a bot
on turn at startup and at the start of every matchmaking tick (`GameService.wakeBots`), so
that an unwatched game between two bots resumes by itself. A wake-up checks the move count cheaply (bot seats are recognised by
id, from the roster loaded at startup) and, if it is a bot's turn, takes an immutable snapshot
of the position under the game's lock and asks for a move outside it: normally from the engine
service (section 2.7) through `ENGINE_URL`, with the in-process engine as a fallback when the
service is unavailable or `ENGINE_URL` is unset (then at most `BOT_PARALLELISM` searches run
at a time, default 2). The request carries a priority: `HUMAN` when a person is at the board,
`BOT` when both seats are bots, and the service serves the humans first. A bot-versus-bot game
is held instead of searched in two cases: while the human load is high (at least
`bot.human_games_limit` games with a human being played, section 2.10; checked before every such
move against a count cached for ten seconds, logged at DEBUG) and when the service stays
overloaded for a quarter of `ENGINE_RETRY_SECONDS` (`EngineBusyException`, logged at INFO; no
fallback to the in-process engine, which would load the API exactly when the service is
busiest). In both cases the game resumes when `GameService.wakeBots` runs at a later
matchmaking tick. A human's game is never held: an overloaded or unavailable service falls
back to the in-process engine. A game is never searched twice at the same time, and a wake-up that
arrives during a search is honoured afterwards. The chosen move goes through the normal move pipeline (`GameService.move`
with the bot's principal), so observations get their outcome from the server and clients
receive the usual `move` events. A reply to a human move is delayed so that at least
`bot.reply_pause_millis` (section 2.11; 700 ms by default) pass after the move (the search time
counts towards it); between two bots the pause is `bot.vs_bot_pause_millis`, nil by default, so
they play as fast as the engine answers. A rejected move (the human resigned
meanwhile, or the engine erred) is logged and followed by a fresh look at the game, a few times
at most; any other failure is logged and left for the next wake-up.

### 2.7 Engine service (`engine-service`)

Searching is the only CPU-heavy work of the platform, so it runs in its own stateless
service that can be scaled independently of the API (`docker compose up -d --scale engine=N`,
or replicas on other hosts). It is a small Ktor application around `engine/`:

```
POST /think   {"gameId":"aZ3kq9...","kind":"QUANTUM","level":"MEDIUM","moves":[GameMove, ...],"priority":"HUMAN"}
              -> 200 {"move": GameMove, "elapsedMillis": 1234}   (an Observe move has no outcome)
              -> 204 when the game is already over
              -> 400 {"error":"validation","message":"..."} for an invalid or illegal history
              -> 503 {"error":"overloaded","message":"..."} when the queue is full for the request (the API retries)
GET  /health  -> 200 {"status":"ok","parallelism":8,"inFlight":3,"queued":0,"queuedBots":0,"helpers":8,"helpersBusy":2}
```

`priority` is who waits for the move: `HUMAN` (a person is at the board; also the meaning of a
request without the field, so that an older API is served first rather than last) or `BOT`
(both seats are bots, section 2.6).

The service replays the move list with `Game.replay` (sub-millisecond for classic chess, about
10 ms for a long quantum game with hundreds of universes, i.e. about 1% of a search) and keeps
a small LRU cache of replayed states keyed by `(gameId, ply)` so that the next request for the
same game only applies the new moves. Searches run on a dispatcher of `ENGINE_PARALLELISM`
threads (default: the CPUs available to the container); requests beyond that wait in a bounded
queue (`ENGINE_QUEUE_LIMIT`, default 4 x parallelism) and are refused with 503 above it. The
queue is served humans first: a thread that frees up goes to the oldest waiting `HUMAN`
request, and a `BOT` request runs only while no human waits. A `HUMAN` request may wait while
fewer than `ENGINE_QUEUE_LIMIT` requests wait in all, a `BOT` request only while fewer than
half of that do, so that half of the queue is kept for humans and bot-versus-bot games are the
first to be refused. `/health` reports the waiting requests as `queued` (all of them) and
`queuedBots` (the `BOT` ones). Under load the thinking budget shrinks: with `w` requests
waiting, whatever their priority, and `p` threads the budget is multiplied by `min(1, p / w)`
but never below `ENGINE_MIN_BUDGET_RATIO` (default 0.25), which keeps the reply latency
bounded instead of letting the queue grow.

A search is not confined to its thread. The service also keeps `ENGINE_HELPER_THREADS` helper
threads (default: half of the search threads, at least one) behind a semaphore of the same size, and a search
borrows whichever are free, without waiting, for the independent parts of its work: a classic
search splits the root at every iterative-deepening depth ("young brothers wait": the previous
best move is searched alone to establish the alpha bound, then the other root moves are searched
concurrently against the bound as it stands when each starts and raise it when they beat it,
each with its own node counter for the time check), and a quantum search refines its candidates
with the opponent's reply the same way. A search on a quiet service thus spreads over the idle
cores, so a bot-versus-bot game waits a fraction of the time per move, while under load the
helpers are all taken and searches degrade to sequential: never more than parallelism plus
helpers threads search at once. The playing strength is unchanged (same depths, budgets and
candidate limits, and the scores match the sequential search; only the choice among equally
scored moves may differ), and `ENGINE_HELPER_THREADS=0` turns the splitting off. `/health`
reports `helpers` and `helpersBusy`.

Configuration: `PORT` (default 8081), `ENGINE_PARALLELISM`, `ENGINE_QUEUE_LIMIT`,
`ENGINE_HELPER_THREADS`, `ENGINE_MIN_BUDGET_RATIO`. The API uses `ENGINE_URL` (compose: `http://engine:8081`), retries
503 with backoff for up to `ENGINE_RETRY_SECONDS` (default 30) and falls back to its in-process
engine after that or on connection errors, so a restart of the service never stalls a game.
That is the behaviour for a human's move; a bot-versus-bot move retries 503 for a quarter of
`ENGINE_RETRY_SECONDS` only and then waits for the next matchmaking tick instead of falling
back (section 2.6), while connection errors and other failures fall back whatever the priority.
The API keeps at most `BOT_REMOTE_CONCURRENCY` (default 64) requests in flight.

Games the API keeps in memory (`GameCache`) are dropped not only by the LRU bound but also after
`GAME_IDLE_MINUTES` (default 30) without activity; they are replayed from the database on the
next access.

### 2.8 Ratings

Every registered user and bot has one rating per variant (`CLASSIC`, `QUANTUM`); the variants
are rated separately because the skills differ. Elo with the FIDE-style K factor, in FIDE's
order: initial rating 1500, K = 40 while the player has fewer than 30 rated games in that
variant (whatever the rating); afterwards 10 for good once the player's peak rating in the
variant (the `peak` of the rating row) has reached 2400, even if the rating has dipped below
it since, and 20 otherwise. Expected score
`E = 1 / (1 + 10^((opponent - own) / 400))`, new rating = `round(own + K * (score - E))` with
score 1 / 0.5 / 0, computed for both players from their current ratings when the game finishes
(read and updated under a row lock, so that games finishing at the same time never lose an
update). The `white_rating_before` / `black_rating_before` columns are the ratings when the
game started, kept for the record; a rating row is created by the first rated result, and a
registered player or bot without one is shown with the initial 1500.

A game is rated when it was created with `rated: true`, both seats are non-guest accounts
(guests only play unrated games; the flag is forced to false when a guest creates or joins)
and both sides made at least one move before it finished; a game that ends earlier (for
example a resignation on move one) finishes normally but changes no rating. A resignation and
a forfeit by abandonment (the stale sweep of section 2.3) score as a loss for the player who
resigned or walked away, exactly like a checkmate. Draw by agreement, stalemate and the other
draw reasons score 0.5 each; a game ended by abandonment without a winner (the admin abort, a
stale game with a bot on turn, a cancelled invitation) is not a draw and changes no rating
whatever its length. Ratings are updated inside the same
transaction that finishes the game and recorded in `rating_changes`; `GameDto.ratingChanges`
reports `{white: {before, after}, black: {before, after}}` for finished rated games and null
otherwise (including a rated game whose result did not count, whose `rated` flag stays true).
`ratings` also keeps wins/draws/losses, the number of rated games and the peak.

### 2.9 Players, search and leaderboard

`PlayerSummary {user: UserRef, ratings: {CLASSIC: RatingSummary|null, QUANTUM: RatingSummary|null}}`
with `RatingSummary {rating, games, wins, draws, losses, peak}` (the `user.rating` of a summary
is null: the ratings are next to it); `LeaderboardEntry` is a `PlayerSummary` plus `rank` and
`rating`, the rating of the requested kind (also set in its `user`). `PlayerProfile` is a
`PlayerSummary` plus `memberSince`, `lastSeen` (null for bots), and `recentGames`
(up to 10 `GameSummary`, newest first). The leaderboard orders by rating, then games, then
username; players without a rated game in that kind are not listed. Search matches a
case-insensitive substring of the username or the display name (a blank query lists
everybody), paged like the game listings. Guests never appear in search, leaderboard or
profiles (404). Bots appear in all three.

`PATCH /api/auth/me` accepts `{locale?, displayName?}`: `displayName` is trimmed, 1–40
characters, or null to clear; it must contain at least one letter or digit and nothing
invisible (400 `validation` otherwise): no control characters (`Char.isISOControl`, which
includes NUL, newlines and tabs) and none of the Unicode categories Cf, Zl and Zp (zero-width
spaces and joiners U+200B–U+200F, bidirectional controls U+202A–U+202E and U+2066–U+2069,
soft hyphens, line and paragraph separators), so that a name is never blank once rendered and
cannot disguise the text around it. `UserProfile` carries `name` and `ratings` like a
`PlayerSummary`. The profile's `recentGames` are only the games the public listing shows
(public ones that are not pending invitations): the profile needs no token, so a private game,
whose id is its only protection, stays between its players.

### 2.10 Bots

Bots are ordinary accounts that play by themselves: the roster is defined in code
(`BotRoster`) and its missing accounts are created at startup (an existing row is left as it
is, section 2.11), each with a username, a display name and an `EngineLevel`. Bots created
through the admin API join the roster after the code-defined ones. Five levels exist: `NOVICE`, `EASY`, `MEDIUM`, `HARD`, `EXPERT`. The roster
(two bots per level except one at `NOVICE` and one at `EXPERT`, eight in total) replaces the
single `computer` account, which is kept as a legacy `MEDIUM` bot named "Computer" for old
games: it still plays (and can still be invited by name) but is neither listed by `/api/bots`
nor used by the matchmaker. A roster name that a person registered before it was reserved is
logged at ERROR and that bot is left out of the roster (the server starts with the others)
until the account is renamed; the person's account is not touched.

Playing: `BotPlayer` wakes whenever the seat on turn belongs to a bot, in any game (a bot may
sit on either side, and two bots may play each other). Moves come from the engine service
(section 2.7) with the seat's level; bot-versus-bot games have no artificial pause by default
(`bot.vs_bot_pause_millis`, section 2.11), each move takes as long as the search (the engine
service uses idle cores to speed a single search up).
Nothing limits how many games a bot plays at once beyond the engine service's capacity; what
limits the games bots play with each other is the human-load gate below.

Invitations: creating a game with `opponent: USER` sets `invitee_id`; only that account may
join, and it may decline (`POST /api/games/{id}/decline`, which deletes the game and notifies
the creator's page through a `game` event with `ABANDONMENT`). A bot invitee joins immediately
(the response of `POST /api/games` already shows the game `ACTIVE`). Invitations are listed by
`GET /api/invitations`; the client polls it for the navigation badge. The creator cancels an
invitation like any WAITING game (`resign`); internally `GameService.cancelInvitation` does the
same but only while the invitation is still pending (re-checked under the game's lock; 409
`game_full` once the invitee accepted, 404 once it was declined), so that a caller acting on
a listing can never resign a game that started meanwhile. A pending invitation is not an open
game: the public listing leaves it out until it is accepted, whatever its visibility.

Matchmaking (`BotMatchmaker`; the loop always runs and its ticks act while `bot.matchmaking`
is true, section 2.11): a loop that sleeps a random `BOT_TICK_MIN_MINUTES`–`BOT_TICK_MAX_MINUTES`
(defaults 3–10) and on each tick keeps the lobby alive without flooding it. The tick first
tops up the live games: while fewer than `BOT_LIVE_GAMES_TARGET` (default 2) `ACTIVE` games
with a bot in both seats exist, two distinct available bots start one (one creates a public
rated game of a random kind and colour, the other joins it at once), at most two new games
per tick so that a raised target fills up over a few ticks. It then does one of the
following, so a tick may both start a live game and act once more:

* if fewer than `BOT_OPEN_GAMES_TARGET` (default 2) public WAITING games created by bots exist,
  a random bot creates one (random kind and colour, rated, public);
* otherwise, if a public WAITING game has waited at least `BOT_JOIN_AFTER_MINUTES` (default 8)
  but is younger than `OPEN_GAME_TTL_HOURS`, whoever created it (another bot included), a
  random bot joins the oldest such game, no dice involved: the creator kept waiting the
  longest gets an opponent first. The candidates are the 20 oldest such games; one whose
  creator is the only available bot is passed over for the next, and so is one that was
  joined or cancelled between the listing and the join (409/404, logged at DEBUG). This is
  how a human who has waited for a while gets an opponent, and a second way bot-versus-bot
  games start;
* otherwise (no game qualified or every join failed), with probability 1/4, a random bot
  invites a random registered human who was seen within the last 7 days, has no pending
  invitation from a bot and received none in the last 6 hours (declined invitations are gone
  from the database, so the loop also remembers whom it invited until it restarts); pending
  bot invitations expire after `BOT_INVITATION_TTL_MINUTES` (default 60) and are deleted by
  the loop at the start of every tick through `cancelInvitation`, so one that was accepted or
  declined between the listing and the deletion is left alone (the 409/404 is logged at
  DEBUG).

The human-load gate: while the human load is high, the bots add no game of their own to the
engine's work. The tick then starts no live game, whatever the live target, and joins no open
game that a bot created (such a game is passed over for the next oldest, as a bot's own game
is); a human's open game is still joined, open games for humans are still created and humans
are still invited. The load is high when at least `bot.human_games_limit` (section 2.11;
default `BOT_HUMAN_GAMES_LIMIT`, 0 switches the gate off) games with a human are being played:
`ACTIVE` games with at least one seat that no bot holds, guests included, whose `updated_at`
lies within the last fifteen minutes (`HumanLoad.ACTIVITY_WINDOW`). The window is what keeps
the gate honest: a game whose players walked away stays `ACTIVE` until the stale sweep
finishes it (up to `game.stale_hours`, section 2.3), and counting such games once kept the
gate closed for a day with nobody at the boards, freezing every bot-versus-bot game.
`HumanLoad` counts at most once every ten seconds (the bot player asks before every
bot-versus-bot move, the matchmaker once per tick); the admin overview shows the same count,
taken afresh, as `humanGames`. The bot
player holds bot-versus-bot moves under the same condition (section 2.6), so the live games
already in progress pause too and resume, through the tick's `wakeBots`, once the load has
dropped below the limit. A tick that held something back says so once at INFO.

Before any of that, every tick also wakes the bots in the `ACTIVE` games where one is on turn
(`GameService.wakeBots`, section 2.6), so a game interrupted by a restart or a failed search
resumes without anybody looking at it, and deletes the public open games that nobody joined
for `OPEN_GAME_TTL_HOURS` (default 24), whoever created them, as abandoned lobbies: they are
cancelled like a WAITING game (deleted, published with `ABANDONMENT`) and are never joined by
a bot; private open games are links their creators share and are kept.

Whenever the matchmaker needs a bot (to create or join an open game, to start a live game or to
invite a human) it takes the least busy one: the fewest games in progress, then the fewest games
ever played, a random one among equals, so games spread over the whole roster and newcomers get
their first rated games. A bot never has more than `BOT_MAX_ACTIVE_GAMES` (default 6) games in progress; the tick skips
bots at the limit (a live game needs two below it; the lobby action does nothing when every
bot is at it). All choices use the injectable `Random` (`nextDouble` for the invitation
probability, `nextInt` for the picks) and `Clock`, so tests drive the loop by calling its
`tick()` directly (its own ticks are minutes apart, so none happens during a test); the tick
returns what it did, the games started first and then the lobby action, and the loop logs
each at INFO.

### 2.11 Administration: runtime settings, admin API and CLI

Operational knobs (of the bots, and the age at which an untouched game is finished) live in
the `settings` table (`key VARCHAR(64) PK, value TEXT, updated_at TIMESTAMPTZ`) and can be
changed at runtime; the environment variables of 2.1 only provide the defaults. Known keys,
all validated on write:

| key | type | default | meaning |
|-----|------|---------|---------|
| `bot.matchmaking` | boolean | `BOT_MATCHMAKING` | the matchmaker loop acts on its ticks (false pauses it) |
| `bot.open_games_target` | int >= 0 | `BOT_OPEN_GAMES_TARGET` | public open games kept waiting by bots |
| `bot.live_games_target` | int >= 0 | `BOT_LIVE_GAMES_TARGET` | bot-versus-bot games kept in progress (0 switches this off) |
| `bot.human_games_limit` | int >= 0 | `BOT_HUMAN_GAMES_LIMIT` | games with a human played within the last fifteen minutes at or above which bots start no games with each other and bot-versus-bot games wait (section 2.10; 0 switches this off) |
| `bot.reply_pause_millis` | int >= 0 | 700 | shortest pause before a bot answers a human |
| `bot.vs_bot_pause_millis` | int >= 0 | 0 | shortest pause between the moves of two bots |
| `bot.max_active_games` | int >= 1 | `BOT_MAX_ACTIVE_GAMES` | games one bot plays at once |
| `bot.join_after_minutes` | int >= 0 | `BOT_JOIN_AFTER_MINUTES` | how long an open game waits before a bot may join |
| `bot.invitation_ttl_minutes` | int >= 1 | `BOT_INVITATION_TTL_MINUTES` | expiry of a bot's invitation |
| `bot.tick_min_minutes` / `bot.tick_max_minutes` | int >= 1 | `BOT_TICK_MIN/MAX_MINUTES` | matchmaker sleep range (max is raised to min when lower) |
| `game.stale_hours` | int >= 0 | `GAME_STALE_HOURS` | hours without a move after which a human on turn forfeits an `ACTIVE` game; a bot on turn: no winner, no rating change (section 2.3; 0 switches the sweep off) |

A `Settings` service caches the table, refreshes it every minute and immediately after a write,
and hands typed values to `BotPlayer` (pauses, read before every move), `BotMatchmaker`
(read at the start of every tick), `HumanLoad` (the limit, read on every check) and
`StaleGames` (the age, read before every sweep). Unknown keys are rejected.

The admin API is enabled only when `ADMIN_TOKEN` (at least 16 characters) is set; every request
must carry `X-Admin-Token` with that value (compared in constant time), otherwise 401
`unauthorized`; with no token configured the routes answer 404. Admin actions are logged.

```
GET   /api/admin/overview                       -> {users, guests, bots, games: {waiting, active, finished}, activeBotGames, humanGames, settings: {...}}
                                                   (activeBotGames: games in progress with a bot seat; humanGames: with a human seat and played
                                                   within the last fifteen minutes, the count the gate of section 2.10 judges)
GET   /api/admin/bots                           -> {bots: [AdminBot]}      AdminBot = PlayerSummary + {level, legacy, activeGames, totalGames}
GET   /api/admin/bots/{username}                -> AdminBotDetails: AdminBot + {activeGameList: [GameSummary], recentGames: [GameSummary] (20)}
POST  /api/admin/bots  {username, displayName, level}  -> 201 AdminBot   (username rules of 2.2, 409 username_taken; the bot joins the roster at once)
PATCH /api/admin/bots/{username} {level?, displayName?} -> 200 AdminBot
GET   /api/admin/games?status=WAITING|ACTIVE|FINISHED&limit=&offset= -> {games: [GameSummary]}   (every game whatever its
                                                                                 visibility, newest first; limit/offset as in 2.3)
POST  /api/admin/games/{id}/abort               -> 200 GameDto   (an ACTIVE or WAITING game is finished by ABANDONMENT
                                                                  with no winner and no rating change, published to its
                                                                  subscribers as a `game` event; 409 game_finished otherwise)
DELETE /api/admin/games/{id}                    -> 204            (deletes the game with its moves and rating changes;
                                                                  the ratings themselves stay; 404 for an unknown id)
GET   /api/admin/settings                       -> {settings: [{key, value, default, source: "db"|"default", description}]}
PUT   /api/admin/settings/{key}  {value: string} -> 200 the setting; 400 validation for unknown keys or bad values
DELETE /api/admin/settings/{key}                -> 200 the setting reset to its default
```

Bots created through the API are stored exactly like the roster's (`is_bot`, `bot_level`,
`display_name`, no password) and the in-memory bot registry is refreshed, so the matchmaker and
the bot player pick them up without a restart. The static roster still seeds the eight default
bots at startup but never deletes or overrides bots changed through the API (level and display
name of a roster bot are only set when the row is created).

The command line tool `admin-cli` (Gradle module; `./gradlew :admin-cli:installDist` produces
`admin-cli/build/install/admin-cli/bin/chess-admin`) wraps this API:

```
chess-admin [--api URL] [--token TOKEN] [--json] <command>
  overview
  bots list
  bots show <username>
  bots spawn --username <name> --name "<display name>" --level NOVICE|EASY|MEDIUM|HARD|EXPERT
  bots set <username> [--level <level>] [--name "<display name>"]
  settings list
  settings set <key> <value>
  settings reset <key>
  settings open-games <n>                 # shortcut for bot.open_games_target
  settings live-games <n>                 # shortcut for bot.live_games_target
  settings human-games-limit <n>          # shortcut for bot.human_games_limit
  games list [--status WAITING|ACTIVE|FINISHED] [--limit <n>]
  games abort <id>                        # finish a game with ABANDONMENT, no rating change
  games drop <id>                         # delete a game outright
  settings pauses [--reply <ms>] [--bots <ms>]   # shortcuts for the two pauses
```

`--api` defaults to `CHESS_API_URL` or `http://localhost:8080`; `--token` defaults to
`CHESS_ADMIN_TOKEN`. Output is a readable table; `--json` prints the API response.

### 2.12 The engine (`engine`)

`ChessEngine` is pure and stateless: it reads an immutable state and returns a move within a
wall-clock budget, so the same code serves the engine service, the API's fallback and, later,
offline clients. Observations come back without an outcome; the server resolves them.

Evaluation (`Evaluation`, shared by both variants, in centipawns): material (100/320/330/500/900)
plus piece-square tables (the "simplified evaluation function"; the king's table switches to its
endgame version once neither side has more than a queen in non-pawn material), plus a mop-up
term for bare endgames: a side ahead by at least a rook against at most a minor piece and a pawn
is rewarded for the defending king's distance from the centre (10 per square) and for the
closeness of the two kings (4 per square), which is what wins such endgames and what the tables
alone never ask for. A draw, by repetition or otherwise, is worth 0 to either side.

Classic chess (`ClassicSearch`): negamax with alpha-beta pruning, iterative deepening up to the
level's depth, MVV-LVA move ordering and a quiescence search on captures (from `MEDIUM` up).
A position that already occurred in the game or earlier in the line scores as a draw, so a side
that is ahead looks for progress instead of shuffling into a threefold repetition and a side
that is behind heads for one.

Quantum chess (`QuantumSearch`): expectimax over the distribution with iterative deepening.
Every candidate (normal move, split of each piece between its best few targets or "stay", and
observation) is scored statically on the resulting distribution and then refined with the
opponent's strongest reply (depth 2): the best `quantumCandidates` always (the first one even
past the deadline, so that there is a refined move to play), the rest, in static order, while
the first half of the budget lasts, so that a quiet position is searched in full and a move
that looks poor at a glance but is the only one to save a piece is not lost to the static cut.
In a bare endgame (one side ahead by at least a rook against at most a minor piece and a
pawn, in at least half of the universes by probability) the best `quantumCandidates` by
depth-2 score are then searched deeper, while the budget allows, up to the level's
`quantumDepth`, two plies at a time so that the opponent always has the last word, where every
inner node orders its replies (normal moves and observations, no splits) statically and
follows the best eight only. An iteration counts only when it completes for every candidate,
so the move always comes from the deepest completed one. The deeper search is what converts a
won endgame (a rook and king against a king in about ten plies at `HARD`) instead of shuffling
for two hundred plies; it is confined to such endgames because in self-play matches at equal
budgets the full-width depth 2 beat a beam-pruned depth 4 in positions with more material,
where the beam misses replies that the full width finds. Observations
are chance nodes (the probability-weighted average over their outcomes); a universe without a
king counts 6000 for the other side; a king attackable on the opponent's turn costs 900; a
distribution that already occurred in the game or earlier in the line is a draw; a quicker win
scores higher. The static evaluation of a distribution is the probability-weighted average over
its universes, and deliberately nothing about their number: the evaluation is zero-sum, so a
term that made a fog of universes cost the side ahead would make the same fog pay the side
behind, which then splits on every move to get it (a 3 percent discount per doubling was
tried: the losing side answered with stay-splits of ordinary moves at every level without a
split cost, and the winning side spent tempi observing three-universe positions). The fog is
kept down at the root instead: at `NOVICE` and `EASY` a split costs 40 centipawns on top of
what it leads to (the level's `splitCost`). Those levels pick at random among the moves within
their randomness margin, and a split is seldom worse than the best move at a glance, so
without the cost they split on almost every move and bury the game in a fog of hundreds of
universes that nobody resolves. The cost does not rule splits out (one still enters the random
pool when it comes within the margin less the cost), it makes them rarer: in `EASY` self-play
splits fell from about 40 to about 20 percent of the moves, the average universe count from
about 400 to about 20, and every game ended. The levels that always take the best move split
only when the search says it pays, and pay nothing; a flat cost at `HARD` lost matches to the
previous search, so it stays confined to the random levels.

| level | classic depth | quiescence | quantum candidates | quantum depth | randomness | split cost | budget |
|-------|---------------|------------|--------------------|---------------|------------|------------|--------|
| `NOVICE` | 1 | no | 4 | 2 | 200 | 40 | 0.3 s |
| `EASY` | 2 | no | 6 | 2 | 90 | 40 | 0.7 s |
| `MEDIUM` | 3 | yes | 12 | 4 | 0 | 0 | 1.5 s |
| `HARD` | 6 | yes | 24 | 4 | 0 | 0 | 3.5 s |
| `EXPERT` | 7 | yes | 32 | 4 | 0 | 0 | 6 s |

Randomness: moves scoring within that many centipawns of the best are chosen at random (with
the injectable `Random`), so the weaker levels vary their play. Both searches spread the
independent parts of their work (root moves, candidates) over a `SearchParallelism`
(section 2.7) without changing the scores they find.

## 3. Web client (`web`)

Vite + React 19 + TypeScript, router and a small store (zustand). The JS core is a dependency
(`"chess-platform-core": "file:../core/build/dist/js/productionLibrary"`), wrapped by
`src/core/game.ts` which parses the JSON strings into typed objects (`src/api/types.ts` mirrors
section 1.3 and 2.3).

Pages: Home (lobby: open public games to join, active public games to watch, create game with
rated/casual and an opponent: open, a bot from the roster, or a player by username), Login,
Register, My games, Game, Players (leaderboard per variant with search), Player profile
(ratings, statistics, recent games, "invite to a game"), Invitations (incoming with
accept/decline, outgoing with cancel; a badge in the navigation shows the count), Settings
(display name, language), Rules (classic and quantum), About (credits: cburnett pieces,
CC BY-SA 3.0). Game headers show each player's rating for the game's variant, a rated/casual
pill, and the rating change once a rated game is over.

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
  move list, share link for private games. A finished game shows its result line
  (`src/core/notation.ts`): "White wins by checkmate", "Draw by stalemate", and for a game
  ended by abandonment without a winner (aborted, cancelled, or a stale game with a bot on turn)
  "Abandoned" rather than a draw; a forfeited stale game reads "White wins by abandonment".
  A rated game that changed no rating gets the note "No rating change: the game ended before
  both sides had moved" (`src/core/ratings.ts`), except one abandoned without a winner, whose
  rating stays for a different reason.

## 4. Deployment

Production runs on Kubernetes (`deploy/k8s`); `docker-compose.yml` runs the same stack on one
host, for development and as a self-contained alternative.

`docker-compose.yml` runs five services: `engine` (`engine-service/Dockerfile`, no published
port, CPU-bound, scalable with `--scale engine=N`), `db` (postgres:17, named volume; published on
`127.0.0.1:5432` only, so that a server started from Gradle can use it), `api`
(`server/Dockerfile`: multi-stage Gradle build on Temurin 21, runs as an unprivileged user,
healthcheck on `/api/health`) and `web` (`web/Dockerfile`: builds the core JS library and the
Vite bundle, served by nginx which also proxies `/api` including WebSockets to `api`; starts
once `api` is healthy) and `caddy` (`caddy:2-alpine`, `deploy/Caddyfile`: the only service with
published ports, 80 and 443). Both Dockerfiles copy the build scripts before the sources and keep
the Gradle and npm caches in BuildKit cache mounts, so a source change does not download the
dependencies again. `POSTGRES_PASSWORD` and `JWT_SECRET` come from `.env` (`.env.example` lists
them); compose refuses to start when either is missing. Mobile clients will talk to the same
`/api` through the TLS proxy (Caddy or the cluster's ingress) and nginx.

In the cluster (namespace `chess`) the same images run as the Deployments `engine`, `api` and
`web` with Services of the same names, so that nginx and the API find each other exactly as
under compose, and PostgreSQL as the StatefulSet `db` with a persistent volume. `api` is a single
replica that is replaced rather than rolled (running games and their sockets live in the
process) and waits for the database in an init container. `engine` requests 4 CPUs and is
limited to 6 (the limit is what the JVM sizes its search threads from) and is scaled by a
`HorizontalPodAutoscaler` (`autoscaling/v2`, 1 to 2 replicas, target 75 % average CPU
utilization; k3s ships the metrics-server it needs): the Deployment declares no replica count,
the autoscaler owns it. The service is stateless (the replay cache is per pod and replaying is
cheap), so the API's requests spread over the replicas through the `engine` Service. Two
replicas use up to 12 of the single node's 16 cores, leaving the rest to the API, the database
and the web server; `maxReplicas` is what to raise when nodes are added. The database password
and the JWT key come from the Secret `chess` (`deploy/k8s/create-secret.sh`).

nginx serves `index.html` with `Cache-Control: no-cache` and the hashed `/assets/` as
immutable, hides its version and adds `X-Content-Type-Options`, `X-Frame-Options` and
`Referrer-Policy` to every response (`deploy/security-headers.conf`). Because only the TLS proxy
(Caddy, or Traefik from the pod network) reaches nginx, nginx trusts the `X-Forwarded-For` header
from the private network ranges and reports the real client address in its access log and in
`X-Real-IP` to the API (which rate limits by it). Container logs go to Docker's json-file driver
with rotation under compose (`docker compose logs <service>`) and to `kubectl logs` in the
cluster.

Deployments are done by the GitHub Actions workflow `deploy.yml`, started by hand from the
Actions tab and only on `master`: it builds the three images, pushes them to GHCR tagged with
the commit SHA and `latest`, runs `deploy/k8s/deploy.sh` with the SHA, which applies the
manifests with the images pinned to it and waits for the rollout, then checks `/api/health`.
The workflow reaches the cluster with the kubeconfig of the service account `deployer`
(`deploy/k8s/ci/deployer.yaml`), which may only change the objects of the `chess` namespace and
cannot read its Secrets. The manifests themselves carry no image tag: `deploy.sh` without one
keeps the version that runs, so applying them never rolls the app back. Rolling back is the same
workflow run on an older commit. `ci.yml` runs the test suites on every push and pull request.

In the cluster TLS is terminated by its Traefik: the Ingress for `quantum-chess.fun` gets its
Let's Encrypt certificate from cert-manager (ClusterIssuer `letsencrypt-prod`, ACME over HTTP),
Traefik redirects HTTP to HTTPS and a Middleware adds `Strict-Transport-Security`.

Under compose TLS is terminated by Caddy. With `SITE_ADDRESS` set to the public hostname it obtains a
Let's Encrypt certificate (ACME over HTTP, so the domain's A record must point at the host and
port 80 must be reachable), renews it automatically, redirects HTTP to HTTPS for that hostname
and adds `Strict-Transport-Security`; `WWW_ADDRESS` names an optional `www.` host that only
redirects to it. Certificates persist in the `caddy-data` volume. Requests
for any other hostname (such as the bare IP address) are served over plain HTTP, and without
`SITE_ADDRESS` the stack serves plain HTTP on localhost for development.
