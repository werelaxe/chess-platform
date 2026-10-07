package ru.werelaxe.chess.engineservice

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
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
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.Square
import ru.werelaxe.chess.engine.ChessEngine
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.engineservice.config.AppConfig
import ru.werelaxe.chess.engineservice.dto.ErrorResponse
import ru.werelaxe.chess.engineservice.dto.HealthResponse
import ru.werelaxe.chess.engineservice.dto.ThinkRequest
import ru.werelaxe.chess.engineservice.dto.ThinkResponse
import ru.werelaxe.chess.engineservice.service.Engine
import ru.werelaxe.chess.engineservice.service.Searcher
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** The real engine with a fresh seeded random for every move, so that its choices are reproducible. */
val SEEDED_ENGINE = Engine { state, level, budget -> ChessEngine(level, Random(1)).chooseMove(state, budget) }

/** Runs [block] against a service wired with [engine] and [config]. */
fun serviceTest(
    config: AppConfig = AppConfig(parallelism = 2, queueLimit = 4),
    engine: Engine = SEEDED_ENGINE,
    block: suspend TestContext.() -> Unit,
) = testApplication {
    val searcher = Searcher(config, engine)
    application { module(searcher) }
    val client = createClient {
        install(ContentNegotiation) { json(ChessJson.json) }
    }
    TestContext(client, searcher).block()
}

class TestContext(val client: HttpClient, val searcher: Searcher) {
    suspend fun thinkResponse(
        gameId: String,
        moves: List<GameMove>,
        kind: GameKind = GameKind.CLASSIC,
        level: EngineLevel = EngineLevel.EASY,
    ): HttpResponse = client.post("/think") {
        contentType(ContentType.Application.Json)
        setBody(ThinkRequest(gameId, kind, level, moves))
    }

    suspend fun thinkRaw(json: String): HttpResponse = client.post("/think") {
        contentType(ContentType.Application.Json)
        setBody(json)
    }

    /** Asks for a move and asserts that it is legal after [moves]. */
    suspend fun think(
        gameId: String,
        moves: List<GameMove>,
        kind: GameKind = GameKind.CLASSIC,
        level: EngineLevel = EngineLevel.EASY,
    ): GameMove {
        val response = thinkResponse(gameId, moves, kind, level)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val move = response.body<ThinkResponse>().move
        val game = Game.replay(kind, moves)
        assertTrue(game.isLegal(game.resolve(move, Random(1))), "illegal move $move after $moves")
        return move
    }

    suspend fun health(): HealthResponse {
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return response.body()
    }

    /** Polls the health endpoint until [condition] holds. */
    suspend fun awaitHealth(condition: (HealthResponse) -> Boolean) {
        val deadline = TimeSource.Monotonic.markNow() + 10.seconds
        while (true) {
            val health = health()
            if (condition(health)) return
            if (deadline.hasPassedNow()) fail("The service did not reach the expected state: $health")
            delay(10)
        }
    }
}

suspend fun HttpResponse.error(): ErrorResponse = body()

/** Asserts the status and the error code of an error response. */
suspend fun HttpResponse.assertError(status: HttpStatusCode, code: String) {
    assertEquals(status, this.status, bodyAsText())
    assertEquals(code, error().error)
}

fun normal(uci: String): GameMove = GameMove.Normal(Square.parse(uci.substring(0, 2)), Square.parse(uci.substring(2, 4)))

fun split(from: String, first: String, second: String): GameMove =
    GameMove.Split(Square.parse(from), Square.parse(first), Square.parse(second))

fun observe(square: String): GameMove = GameMove.Observe(Square.parse(square))

/** The moves of a game, as a client would send them. */
fun moves(vararg uci: String): List<GameMove> = uci.map(::normal)

/** 1. f3 e5 2. g4 Qh4#: the shortest possible checkmate. */
val FOOLS_MATE: List<GameMove> = moves("f2f3", "e7e5", "g2g4", "d8h4")
