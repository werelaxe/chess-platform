package ru.werelaxe.chess.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.bearerAuth
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
import ru.werelaxe.chess.core.ChessJson
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.PieceType
import ru.werelaxe.chess.core.Square
import ru.werelaxe.chess.server.config.AppConfig
import ru.werelaxe.chess.server.dto.AuthResponse
import ru.werelaxe.chess.server.dto.ColorChoice
import ru.werelaxe.chess.server.dto.CreateGameRequest
import ru.werelaxe.chess.server.dto.CredentialsRequest
import ru.werelaxe.chess.server.dto.DrawAction
import ru.werelaxe.chess.server.dto.DrawRequest
import ru.werelaxe.chess.server.dto.ErrorResponse
import ru.werelaxe.chess.server.dto.GameDto
import ru.werelaxe.chess.server.dto.MoveRequest
import ru.werelaxe.chess.server.dto.MoveResponse
import ru.werelaxe.chess.server.model.Visibility
import ru.werelaxe.chess.server.repository.Repositories
import ru.werelaxe.chess.server.repository.memory.InMemoryGameRepository
import ru.werelaxe.chess.server.repository.memory.InMemoryUserRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.assertEquals

const val TEST_PASSWORD = "correct-horse"

val TEST_CONFIG = AppConfig(jwtSecret = "test-secret", bcryptCost = 4)

/** Runs [block] against a server wired with fresh in-memory repositories and no database. */
fun serverTest(seed: Int = 42, block: suspend TestContext.() -> Unit) = testApplication {
    val repositories = Repositories(InMemoryUserRepository(), InMemoryGameRepository())
    val clock = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC)
    application {
        module(TEST_CONFIG, repositories, random = Random(seed), clock = clock)
    }
    val client = createClient {
        install(ContentNegotiation) { json(ChessJson.json) }
        install(WebSockets)
    }
    TestContext(client).block()
}

class TestContext(val client: HttpClient) {
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

    suspend fun createGameResponse(
        token: String,
        kind: GameKind = GameKind.CLASSIC,
        visibility: Visibility = Visibility.PUBLIC,
        color: ColorChoice = ColorChoice.WHITE,
    ): HttpResponse = client.post("/api/games") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(CreateGameRequest(kind, visibility, color))
    }

    suspend fun createGame(
        token: String,
        kind: GameKind = GameKind.CLASSIC,
        visibility: Visibility = Visibility.PUBLIC,
        color: ColorChoice = ColorChoice.WHITE,
    ): GameDto {
        val response = createGameResponse(token, kind, visibility, color)
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsString())
        return response.body()
    }

    suspend fun joinResponse(token: String, id: String): HttpResponse =
        client.post("/api/games/$id/join") { bearerAuth(token) }

    suspend fun join(token: String, id: String): GameDto {
        val response = joinResponse(token, id)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsString())
        return response.body()
    }

    /** Creates a game as [white] and joins it as [black]; returns the active game. */
    suspend fun activeGame(white: String, black: String, kind: GameKind = GameKind.CLASSIC): GameDto {
        val created = createGame(white, kind = kind, color = ColorChoice.WHITE)
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
