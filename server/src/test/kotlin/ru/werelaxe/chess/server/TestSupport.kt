package ru.werelaxe.chess.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import ru.werelaxe.chess.core.ChessJson
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.PieceType
import ru.werelaxe.chess.core.Square
import ru.werelaxe.chess.engine.ChessEngine
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.server.config.AppConfig
import ru.werelaxe.chess.server.dto.AuthResponse
import ru.werelaxe.chess.server.dto.ColorChoice
import ru.werelaxe.chess.server.dto.CreateGameRequest
import ru.werelaxe.chess.server.dto.CredentialsRequest
import ru.werelaxe.chess.server.dto.DrawAction
import ru.werelaxe.chess.server.dto.DrawRequest
import ru.werelaxe.chess.server.dto.ErrorResponse
import ru.werelaxe.chess.server.dto.GameDto
import ru.werelaxe.chess.server.dto.GameSummary
import ru.werelaxe.chess.server.dto.GamesResponse
import ru.werelaxe.chess.server.dto.InvitationsResponse
import ru.werelaxe.chess.server.dto.MoveRequest
import ru.werelaxe.chess.server.dto.MoveResponse
import ru.werelaxe.chess.server.dto.Opponent
import ru.werelaxe.chess.server.dto.PlayerProfile
import ru.werelaxe.chess.server.dto.UserProfile
import ru.werelaxe.chess.server.dto.UserRef
import ru.werelaxe.chess.server.model.Visibility
import ru.werelaxe.chess.server.repository.Repositories
import ru.werelaxe.chess.server.repository.memory.InMemoryStore
import ru.werelaxe.chess.server.service.BotRoster
import ru.werelaxe.chess.server.service.InProcessMoveProvider
import ru.werelaxe.chess.server.service.MoveProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

const val TEST_PASSWORD = "correct-horse"

/**
 * Bots reply almost at once. The matchmaker loop sleeps minutes between its ticks, so it never
 * acts during a test: the tests that need it drive its tick by hand. No bot-versus-bot game is
 * started by a tick (the live target is 0): the tests of that feature switch it on, with a
 * scripted move provider so that the bots do not search for real in the background.
 */
val TEST_CONFIG = AppConfig(jwtSecret = "test-secret", bcryptCost = 4, botReplyPauseMillis = 10, botLiveGamesTarget = 0)

val TEST_START: Instant = Instant.parse("2026-10-07T10:00:00Z")

/** A clock the tests move forward by hand. */
class MutableClock(@Volatile private var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now

    fun advance(duration: java.time.Duration) {
        now = now.plus(duration)
    }
}

/**
 * A [Random] for driving the matchmaker: the probability rolls come from [rolls] (and are
 * 1.0, "never", once those run out), every pick is the first element and every coin is tails.
 */
class ScriptedRandom(vararg rolls: Double) : Random() {
    val rolls = ArrayDeque(rolls.toList())

    override fun nextBits(bitCount: Int): Int = 0

    override fun nextDouble(): Double = rolls.removeFirstOrNull() ?: 1.0

    override fun nextInt(from: Int, until: Int): Int = from

    override fun nextBoolean(): Boolean = false
}

/** The roll of a [ScriptedRandom] that makes the matchmaker invite a human; with none queued the dice say no. */
const val INVITE = 0.1

/** The username of a roster bot of [level]. */
fun botNamed(level: EngineLevel): String = BotRoster.specs.first { it.level == level && !it.legacy }.username

/**
 * Runs [block] against a server wired with in-memory repositories and no database: fresh ones
 * unless the test passes [repositories] it filled beforehand (rows that must exist when the
 * application starts, or decorated repositories). The bots' moves come from [moveProvider]: by
 * default the in-process engine at the easy level whatever level a game asks for, with a fresh
 * seeded random for every move so that its choices are reproducible.
 */
fun serverTest(
    seed: Int = 42,
    config: AppConfig = TEST_CONFIG,
    moveProvider: MoveProvider = InProcessMoveProvider({ ChessEngine(EngineLevel.EASY, Random(seed)) }),
    random: Random = Random(seed),
    repositories: Repositories = InMemoryStore().repositories(),
    block: suspend TestContext.() -> Unit,
) = testApplication {
    val clock = MutableClock(TEST_START)
    lateinit var services: AppServices
    application {
        services = module(
            config,
            repositories,
            random = random,
            clock = clock,
            moveProvider = moveProvider,
        )
    }
    val client = createClient {
        install(ContentNegotiation) { json(ChessJson.json) }
        install(WebSockets)
    }
    // The application starts lazily, on the first request; the health check brings the services up.
    assertEquals(HttpStatusCode.OK, client.get("/api/health").status)
    TestContext(client, repositories, services, clock, random).block()
}

class TestContext(
    val client: HttpClient,
    val repositories: Repositories,
    val services: AppServices,
    val clock: MutableClock,
    /** The application's random, for the tests that script it. */
    val random: Random,
) {
    suspend fun register(username: String, password: String = TEST_PASSWORD): HttpResponse =
        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(CredentialsRequest(username, password))
        }

    /** Registers a user and returns the token. */
    suspend fun token(username: String): String {
        val response = register(username)
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsString())
        return response.body<AuthResponse>().token
    }

    suspend fun login(username: String, password: String = TEST_PASSWORD): HttpResponse =
        client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(CredentialsRequest(username, password))
        }

    suspend fun guestResponse(): HttpResponse = client.post("/api/auth/guest")

    /** Creates a guest account and returns its token and user. */
    suspend fun guest(): AuthResponse {
        val response = guestResponse()
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsString())
        return response.body()
    }

    suspend fun me(token: String): UserProfile {
        val response = client.get("/api/auth/me") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsString())
        return response.body()
    }

    /** `PATCH /api/auth/me` with a raw JSON body. */
    suspend fun patchMe(token: String, body: String): HttpResponse =
        client.patch("/api/auth/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    suspend fun setLocale(token: String, locale: String?): HttpResponse =
        patchMe(token, """{"locale":${locale?.let { "\"$it\"" } ?: "null"}}""")

    suspend fun setDisplayName(token: String, name: String?): HttpResponse =
        patchMe(token, """{"displayName":${name?.let { "\"$it\"" } ?: "null"}}""")

    suspend fun createGameResponse(
        token: String,
        kind: GameKind = GameKind.CLASSIC,
        visibility: Visibility = Visibility.PUBLIC,
        color: ColorChoice = ColorChoice.WHITE,
        rated: Boolean = true,
        opponent: Opponent = Opponent.OPEN,
        opponentUsername: String? = null,
    ): HttpResponse = client.post("/api/games") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(CreateGameRequest(kind, visibility, color, rated, opponent, opponentUsername))
    }

    suspend fun createGame(
        token: String,
        kind: GameKind = GameKind.CLASSIC,
        visibility: Visibility = Visibility.PUBLIC,
        color: ColorChoice = ColorChoice.WHITE,
        rated: Boolean = true,
        opponent: Opponent = Opponent.OPEN,
        opponentUsername: String? = null,
    ): GameDto {
        val response = createGameResponse(token, kind, visibility, color, rated, opponent, opponentUsername)
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsString())
        return response.body()
    }

    /** Invites [opponent] to a game in which the caller plays [color]. */
    suspend fun invite(
        token: String,
        opponent: String,
        kind: GameKind = GameKind.CLASSIC,
        color: ColorChoice = ColorChoice.WHITE,
        visibility: Visibility = Visibility.PUBLIC,
        rated: Boolean = true,
    ): GameDto = createGame(token, kind, visibility, color, rated, Opponent.USER, opponent)

    /** Creates a game against a roster bot of [level], in which the caller plays [color]; it is active at once. */
    suspend fun botGame(
        token: String,
        kind: GameKind = GameKind.CLASSIC,
        color: ColorChoice = ColorChoice.WHITE,
        level: EngineLevel = EngineLevel.EASY,
        visibility: Visibility = Visibility.PUBLIC,
        rated: Boolean = true,
    ): GameDto = invite(token, botNamed(level), kind, color, visibility, rated)

    suspend fun myGames(token: String): List<GameSummary> =
        client.get("/api/games/mine") { bearerAuth(token) }.body<GamesResponse>().games

    suspend fun invitations(token: String): InvitationsResponse =
        client.get("/api/invitations") { bearerAuth(token) }.body()

    suspend fun profileResponse(username: String): HttpResponse = client.get("/api/players/$username")

    suspend fun profile(username: String): PlayerProfile {
        val response = profileResponse(username)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsString())
        return response.body()
    }

    /**
     * Waits until the game has at least [count] moves. Polls the caller's listing, which never
     * wakes a bot, so that only the trigger under test can have made it move.
     */
    suspend fun awaitMoves(token: String, id: String, count: Int): GameSummary {
        val deadline = TimeSource.Monotonic.markNow() + 10.seconds
        while (true) {
            val summary = myGames(token).firstOrNull { it.id == id } ?: fail("Game $id is not listed")
            if (summary.moveCount >= count) return summary
            if (deadline.hasPassedNow()) fail("Game $id has ${summary.moveCount} moves, expected $count")
            delay(25)
        }
    }

    /** Like [awaitMoves] but through the repository, for games the test's users do not take part in. */
    suspend fun awaitStoredMoves(id: String, count: Int): Int {
        val deadline = TimeSource.Monotonic.markNow() + 10.seconds
        while (true) {
            val moves = repositories.games.findById(id)?.moveCount ?: fail("Game $id is gone")
            if (moves >= count) return moves
            if (deadline.hasPassedNow()) fail("Game $id has $moves moves, expected $count")
            delay(25)
        }
    }

    suspend fun joinResponse(token: String, id: String): HttpResponse =
        client.post("/api/games/$id/join") { bearerAuth(token) }

    suspend fun join(token: String, id: String): GameDto {
        val response = joinResponse(token, id)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsString())
        return response.body()
    }

    suspend fun decline(token: String, id: String): HttpResponse =
        client.post("/api/games/$id/decline") { bearerAuth(token) }

    /** Creates a game as [white] and joins it as [black]; returns the active game. */
    suspend fun activeGame(white: String, black: String, kind: GameKind = GameKind.CLASSIC, rated: Boolean = true): GameDto {
        val created = createGame(white, kind = kind, color = ColorChoice.WHITE, rated = rated)
        return join(black, created.id)
    }

    suspend fun moveResponse(token: String, id: String, move: GameMove): HttpResponse =
        client.post("/api/games/$id/moves") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(MoveRequest(move))
        }

    suspend fun move(token: String, id: String, move: GameMove): MoveResponse {
        val response = moveResponse(token, id, move)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsString())
        return response.body()
    }

    suspend fun move(token: String, id: String, uci: String): MoveResponse = move(token, id, normal(uci))

    /** Plays the fool's mate: black wins on the fourth ply. */
    suspend fun foolsMate(white: String, black: String, id: String) {
        move(white, id, "f2f3")
        move(black, id, "e7e5")
        move(white, id, "g2g4")
        move(black, id, "d8h4")
    }

    suspend fun resign(token: String, id: String): HttpResponse =
        client.post("/api/games/$id/resign") { bearerAuth(token) }

    suspend fun draw(token: String, id: String, action: DrawAction): HttpResponse =
        client.post("/api/games/$id/draw") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(DrawRequest(action))
        }

    suspend fun getGame(id: String): HttpResponse = client.get("/api/games/$id")

    suspend fun game(id: String): GameDto {
        val response = getGame(id)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsString())
        return response.body()
    }
}

suspend fun HttpResponse.bodyAsString(): String = bodyAsText()

suspend fun HttpResponse.error(): ErrorResponse = body()

/** Asserts the status and the error code of an error response. */
suspend fun HttpResponse.assertError(status: HttpStatusCode, code: String) {
    assertEquals(status, this.status, bodyAsString())
    assertEquals(code, error().error)
}

fun normal(uci: String, promotion: PieceType? = null): GameMove =
    GameMove.Normal(Square.parse(uci.substring(0, 2)), Square.parse(uci.substring(2, 4)), promotion)

fun split(from: String, first: String, second: String): GameMove =
    GameMove.Split(Square.parse(from), Square.parse(first), Square.parse(second))

fun observe(square: String): GameMove = GameMove.Observe(Square.parse(square))

/** The own profile as other players see it inside game DTOs, with [rating] in the game's kind. */
fun UserProfile.ref(rating: Int? = null): UserRef = UserRef(id, username, name, guest, bot, botLevel, rating)
