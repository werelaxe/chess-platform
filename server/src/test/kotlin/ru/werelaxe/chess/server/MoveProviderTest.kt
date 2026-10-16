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
import ru.werelaxe.chess.engine.ThinkPriority
import ru.werelaxe.chess.server.service.EngineBusyException
import ru.werelaxe.chess.server.service.FallbackMoveProvider
import ru.werelaxe.chess.server.service.InProcessMoveProvider
import ru.werelaxe.chess.server.service.MoveProvider
import ru.werelaxe.chess.server.service.RemoteMoveProvider
import java.net.ConnectException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    /** A service whose queue is always full. */
    private fun overloaded() = RemoteMoveProvider(
        MockEngine {
            respond(
                """{"error":"overloaded","message":"The queue is full"}""",
                HttpStatusCode.ServiceUnavailable,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        },
        "http://engine:8081",
        retryFor = 100.milliseconds,
        initialBackoff = 5.milliseconds,
    )

    /** A service that answers every request with an internal error. */
    private fun failing() = RemoteMoveProvider(
        MockEngine {
            respond(
                """{"error":"internal","message":"The engine blew up"}""",
                HttpStatusCode.InternalServerError,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        },
        "http://engine:8081",
    )

    private val untouched = object : MoveProvider {
        override suspend fun chooseMove(
            gameId: String,
            kind: GameKind,
            level: EngineLevel,
            moves: List<GameMove>,
            priority: ThinkPriority,
        ): GameMove? = fail("The fallback must not be asked")
    }

    @Test
    fun inProcessProviderSearchesTheReplayedPosition() = runBlocking {
        inProcess().use { provider ->
            val reply = assertNotNull(provider.chooseMove("g", GameKind.CLASSIC, EngineLevel.MEDIUM, opening, ThinkPriority.HUMAN))
            assertTrue(Game.replay(GameKind.CLASSIC, opening).isLegal(reply), "$reply is legal after 1. e4")
            val quantum = assertNotNull(provider.chooseMove("q", GameKind.QUANTUM, EngineLevel.MEDIUM, emptyList(), ThinkPriority.BOT))
            assertTrue(Game(GameKind.QUANTUM).isLegal(quantum), "$quantum is legal in the initial position")
        }
    }

    /** [plies] pawn splits from the a-file on, each doubling the universes: a fog of 2^[plies]. */
    private fun pawnFog(plies: Int): List<GameMove> = (0 until plies).map { ply ->
        val file = "abcdefgh"[ply / 2]
        if (ply % 2 == 0) split("${file}2", "${file}3", "${file}4") else split("${file}7", "${file}6", "${file}5")
    }

    @Test
    fun inProcessProviderSearchesAFogAboveTheCeilingShallowly() = runBlocking {
        val levels = CopyOnWriteArrayList<EngineLevel>()
        val engines = { level: EngineLevel -> levels += level; ChessEngine(level, Random(7)) }
        InProcessMoveProvider(engines, parallelism = 1, maxUniverses = 4).use { provider ->
            // Eight universes above a ceiling of four: a NOVICE search on the likeliest four, legal in all eight.
            val fog = pawnFog(3)
            val reply = assertNotNull(provider.chooseMove("q", GameKind.QUANTUM, EngineLevel.HARD, fog, ThinkPriority.HUMAN))
            val game = Game.replay(GameKind.QUANTUM, fog)
            assertTrue(game.isLegal(game.resolve(reply, Random(1))), "$reply is legal in the fog")
            assertEquals(listOf(EngineLevel.NOVICE), levels)

            // Within the ceiling the level asked for searches the position as it stands.
            assertNotNull(provider.chooseMove("q", GameKind.QUANTUM, EngineLevel.EASY, pawnFog(2), ThinkPriority.HUMAN))
            assertEquals(listOf(EngineLevel.NOVICE, EngineLevel.EASY), levels)
        }
    }

    @Test
    fun inProcessProviderReportsAFinishedGame() = runBlocking {
        val foolsMate = listOf(normal("f2f3"), normal("e7e5"), normal("g2g4"), normal("d8h4"))
        inProcess().use { assertNull(it.chooseMove("g", GameKind.CLASSIC, EngineLevel.EASY, foolsMate, ThinkPriority.HUMAN)) }
    }

    @Test
    fun fallbackSearchesForAHumanWhenTheServiceIsUnreachable() = runBlocking {
        FallbackMoveProvider(unreachable(), inProcess()).use { provider ->
            val reply = assertNotNull(provider.chooseMove("g", GameKind.CLASSIC, EngineLevel.EASY, opening, ThinkPriority.HUMAN))
            assertTrue(Game.replay(GameKind.CLASSIC, opening).isLegal(reply), "$reply is legal after 1. e4")
        }
    }

    @Test
    fun fallbackSearchesForAHumanWhenTheServiceAnswersAnError() = runBlocking {
        FallbackMoveProvider(failing(), inProcess()).use { provider ->
            val reply = assertNotNull(provider.chooseMove("g", GameKind.CLASSIC, EngineLevel.EASY, opening, ThinkPriority.HUMAN))
            assertTrue(Game.replay(GameKind.CLASSIC, opening).isLegal(reply), "$reply is legal after 1. e4")
        }
    }

    @Test
    fun fallbackLetsABotVersusBotGameWaitForAnUnreachableService() = runBlocking {
        FallbackMoveProvider(unreachable(), untouched).use { provider ->
            val error = assertFailsWith<EngineBusyException> {
                provider.chooseMove("g", GameKind.CLASSIC, EngineLevel.EASY, opening, ThinkPriority.BOT)
            }
            val message = error.message.orEmpty()
            assertTrue("bot-versus-bot game g at ply 1" in message && "Connection refused" in message, message)
        }
    }

    @Test
    fun fallbackLetsABotVersusBotGameWaitWhenTheServiceAnswersAnError() = runBlocking {
        FallbackMoveProvider(failing(), untouched).use { provider ->
            val error = assertFailsWith<EngineBusyException> {
                provider.chooseMove("g", GameKind.CLASSIC, EngineLevel.EASY, opening, ThinkPriority.BOT)
            }
            val message = error.message.orEmpty()
            assertTrue("500" in message && "The engine blew up" in message, message)
        }
    }

    @Test
    fun fallbackSearchesForAHumanWhenTheServiceIsOverloaded() = runBlocking {
        FallbackMoveProvider(overloaded(), inProcess()).use { provider ->
            val reply = assertNotNull(provider.chooseMove("g", GameKind.CLASSIC, EngineLevel.EASY, opening, ThinkPriority.HUMAN))
            assertTrue(Game.replay(GameKind.CLASSIC, opening).isLegal(reply), "$reply is legal after 1. e4")
        }
    }

    @Test
    fun fallbackLetsABotVersusBotGameWaitForAnOverloadedService() = runBlocking {
        FallbackMoveProvider(overloaded(), untouched).use { provider ->
            assertFailsWith<EngineBusyException> {
                provider.chooseMove("g", GameKind.CLASSIC, EngineLevel.EASY, opening, ThinkPriority.BOT)
            }
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
        FallbackMoveProvider(remote, untouched).use {
            assertEquals(normal("c7c5"), it.chooseMove("g", GameKind.CLASSIC, EngineLevel.HARD, opening, ThinkPriority.HUMAN))
        }
    }

    @Test
    fun configurationSelectsTheProvider() {
        MoveProvider.fromConfig(TEST_CONFIG).use { assertIs<InProcessMoveProvider>(it) }
        MoveProvider.fromConfig(TEST_CONFIG.copy(engineUrl = "http://engine:8081")).use { assertIs<FallbackMoveProvider>(it) }
    }
}
