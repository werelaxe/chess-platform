package ru.werelaxe.chess.server

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import ru.werelaxe.chess.core.ChessJson
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.engine.ChessEngine
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.server.service.FallbackMoveProvider
import ru.werelaxe.chess.server.service.InProcessMoveProvider
import ru.werelaxe.chess.server.service.MoveProvider
import ru.werelaxe.chess.server.service.RemoteMoveProvider
import java.net.ConnectException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

class MoveProviderTest {
    private val opening = listOf(normal("e2e4"))

    private fun inProcess() = InProcessMoveProvider({ ChessEngine(EngineLevel.EASY, Random(7)) }, parallelism = 1)

    private fun unreachable() = RemoteMoveProvider(
        MockEngine { throw ConnectException("Connection refused") },
        "http://engine:8081",
        retryFor = 100.milliseconds,
    )

    @Test
    fun inProcessProviderSearchesTheReplayedPosition() = runBlocking {
        inProcess().use { provider ->
            val reply = assertNotNull(provider.chooseMove("g", GameKind.CLASSIC, EngineLevel.MEDIUM, opening))
            assertTrue(Game.replay(GameKind.CLASSIC, opening).isLegal(reply), "$reply is legal after 1. e4")
            val quantum = assertNotNull(provider.chooseMove("q", GameKind.QUANTUM, EngineLevel.MEDIUM, emptyList()))
            assertTrue(Game(GameKind.QUANTUM).isLegal(quantum), "$quantum is legal in the initial position")
        }
    }

    @Test
    fun inProcessProviderReportsAFinishedGame() = runBlocking {
        val foolsMate = listOf(normal("f2f3"), normal("e7e5"), normal("g2g4"), normal("d8h4"))
        inProcess().use { assertNull(it.chooseMove("g", GameKind.CLASSIC, EngineLevel.EASY, foolsMate)) }
    }

    @Test
    fun fallbackUsesTheInProcessEngineWhenTheServiceFails() = runBlocking {
        FallbackMoveProvider(unreachable(), inProcess()).use { provider ->
            val reply = assertNotNull(provider.chooseMove("g", GameKind.CLASSIC, EngineLevel.EASY, opening))
            assertTrue(Game.replay(GameKind.CLASSIC, opening).isLegal(reply), "$reply is legal after 1. e4")
        }
    }

    @Test
    fun fallbackPrefersTheServicesMove() = runBlocking {
        val remote = RemoteMoveProvider(
            MockEngine {
                respond(
                    """{"move":${ChessJson.encodeMove(normal("c7c5"))},"elapsedMillis":5}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
            "http://engine:8081",
        )
        val untouched = object : MoveProvider {
            override suspend fun chooseMove(gameId: String, kind: GameKind, level: EngineLevel, moves: List<GameMove>): GameMove? =
                fail("The fallback must not be asked while the service works")
        }
        FallbackMoveProvider(remote, untouched).use {
            assertEquals(normal("c7c5"), it.chooseMove("g", GameKind.CLASSIC, EngineLevel.HARD, opening))
        }
    }

    @Test
    fun configurationSelectsTheProvider() {
        MoveProvider.fromConfig(TEST_CONFIG).use { assertIs<InProcessMoveProvider>(it) }
        MoveProvider.fromConfig(TEST_CONFIG.copy(engineUrl = "http://engine:8081")).use { assertIs<FallbackMoveProvider>(it) }
    }
}
