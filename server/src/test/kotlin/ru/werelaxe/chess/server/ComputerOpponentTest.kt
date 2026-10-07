package ru.werelaxe.chess.server

import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import ru.werelaxe.chess.core.ChessJson
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.server.dto.AuthResponse
import ru.werelaxe.chess.server.dto.ColorChoice
import ru.werelaxe.chess.server.dto.DrawAction
import ru.werelaxe.chess.server.dto.GameDto
import ru.werelaxe.chess.server.dto.GamesResponse
import ru.werelaxe.chess.server.dto.Opponent
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.model.GameRecord
import ru.werelaxe.chess.server.model.GameResult
import ru.werelaxe.chess.server.model.Visibility
import ru.werelaxe.chess.server.service.BotUser
import ru.werelaxe.chess.server.service.MoveProvider
import ru.werelaxe.chess.server.ws.ServerEvent
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class ComputerOpponentTest {
    private suspend fun ReceiveChannel<Frame>.nextEvent(): ServerEvent = withTimeout(10_000) {
        val frame = assertIs<Frame.Text>(receive())
        ChessJson.json.decodeFromString(ServerEvent.serializer(), frame.readText())
    }

    /** Asserts that the stored moves replay without errors, i.e. every computer move was legal. */
    private fun assertReplays(game: GameDto) {
        assertEquals(game.moveCount, Game.replay(game.kind, game.moves).moveCount)
    }

    @Test
    fun computerRepliesToTheHumansMoves() = serverTest {
        val alice = token("alice")
        val created = computerGame(alice, color = ColorChoice.WHITE, level = EngineLevel.MEDIUM)
        assertEquals(GamePhase.ACTIVE, created.status)
        assertEquals(Visibility.PRIVATE, created.visibility)
        assertEquals(EngineLevel.MEDIUM, created.botLevel)
        assertEquals("alice", created.white?.username)
        assertEquals(BotUser.USERNAME, created.black?.username)
        assertTrue(created.black!!.bot)
        assertFalse(created.white!!.bot)

        move(alice, created.id, "e2e4")
        assertEquals(2, awaitMoves(alice, created.id, 2).moveCount)
        val afterReply = game(created.id)
        assertEquals(GamePhase.ACTIVE, afterReply.status)
        assertIs<GameMove.Normal>(afterReply.moves[1])
        assertReplays(afterReply)

        // 2. d4 is legal whatever black answered, and the computer replies again.
        move(alice, created.id, "d2d4")
        assertEquals(4, awaitMoves(alice, created.id, 4).moveCount)
        assertReplays(game(created.id))
    }

    @Test
    fun computerOpensWhenItPlaysWhite() = serverTest {
        val alice = token("alice")
        val created = computerGame(alice, color = ColorChoice.BLACK)
        assertEquals(BotUser.USERNAME, created.white?.username)
        assertEquals("alice", created.black?.username)

        assertEquals(1, awaitMoves(alice, created.id, 1).moveCount)
        val opened = game(created.id)
        assertIs<GameMove.Normal>(opened.moves.single())
        assertReplays(opened)
        moveResponse(alice, created.id, normal("e2e4")).assertError(HttpStatusCode.BadRequest, "illegal_move")
        move(alice, created.id, "e7e5")
        assertEquals(3, awaitMoves(alice, created.id, 3).moveCount)
    }

    @Test
    fun randomColorSeatsTheComputerOpposite() = serverTest {
        val alice = token("alice")
        val seats = (1..8).map { computerGame(alice, color = ColorChoice.RANDOM) }.map { game ->
            assertTrue(game.white!!.bot != game.black!!.bot, "exactly one seat is the computer")
            if (game.white.bot) Color.WHITE else Color.BLACK
        }.toSet()
        assertEquals(setOf(Color.WHITE, Color.BLACK), seats)
    }

    @Test
    fun quantumComputerMovesAreLegalAndReplayable() = serverTest {
        val alice = token("alice")
        val created = computerGame(alice, kind = GameKind.QUANTUM, color = ColorChoice.WHITE)

        move(alice, created.id, split("e2", "e3", "e4"))
        assertEquals(2, awaitMoves(alice, created.id, 2).moveCount)
        val afterReply = game(created.id)
        val reply = afterReply.moves[1]
        if (reply is GameMove.Observe) assertNotNull(reply.outcome, "the server resolves the computer's observations")
        assertReplays(afterReply)

        // d2-d4 cannot be blocked by one black move, so it is legal in every universe.
        move(alice, created.id, "d2d4")
        assertEquals(4, awaitMoves(alice, created.id, 4).moveCount)
        assertReplays(game(created.id))
    }

    /** What the computer player asked a provider for. */
    private data class Think(val gameId: String, val kind: GameKind, val level: EngineLevel, val moves: List<GameMove>)

    @Test
    fun computerMovesComeFromTheInjectedProviderWithTheGameSnapshot() {
        val thinks = CopyOnWriteArrayList<Think>()
        val scripted = object : MoveProvider {
            override suspend fun chooseMove(gameId: String, kind: GameKind, level: EngineLevel, moves: List<GameMove>): GameMove? {
                thinks += Think(gameId, kind, level, moves)
                return when (moves.size) {
                    1 -> normal("e7e5")
                    3 -> normal("b8c6")
                    else -> null
                }
            }
        }
        serverTest(moveProvider = scripted) {
            val alice = token("alice")
            val created = computerGame(alice, kind = GameKind.CLASSIC, color = ColorChoice.WHITE, level = EngineLevel.HARD)
            move(alice, created.id, "e2e4")
            assertEquals(2, awaitMoves(alice, created.id, 2).moveCount)
            assertEquals(listOf(normal("e2e4"), normal("e7e5")), game(created.id).moves)
            assertEquals(listOf(Think(created.id, GameKind.CLASSIC, EngineLevel.HARD, listOf(normal("e2e4")))), thinks)

            move(alice, created.id, "g1f3")
            assertEquals(4, awaitMoves(alice, created.id, 4).moveCount)
            assertEquals(normal("b8c6"), game(created.id).moves[3])
            assertEquals(listOf(normal("e2e4"), normal("e7e5"), normal("g1f3")), thinks.last().moves)
            assertEquals(2, thinks.size, "one request per computer move")
        }
    }

    @Test
    fun computerMovesArriveAsMoveEventsAfterAPause() = serverTest(botPause = 400.milliseconds) {
        val alice = token("alice")
        val created = computerGame(alice, color = ColorChoice.WHITE)

        client.webSocket("/api/games/${created.id}/ws") {
            assertEquals(0, assertIs<ServerEvent.GameUpdated>(incoming.nextEvent()).game.moveCount)
            val moved = TimeSource.Monotonic.markNow()
            move(alice, created.id, "e2e4")
            assertEquals(0, assertIs<ServerEvent.Move>(incoming.nextEvent()).ply)
            val reply = assertIs<ServerEvent.Move>(incoming.nextEvent())
            assertEquals(1, reply.ply)
            assertTrue(moved.elapsedNow() >= 400.milliseconds, "the reply came after ${moved.elapsedNow()}")
        }
    }

    @Test
    fun lostComputerMoveIsMadeWhenTheGameIsLookedAt() = serverTest {
        val alice = register("alice").body<AuthResponse>()
        val computer = assertNotNull(repositories.users.findByUsername(BotUser.USERNAME))
        assertTrue(computer.isBot)

        // As left behind by a server that stopped before the computer could reply to 1. e4.
        suspend fun interrupted(id: String): GameRecord {
            val now = Instant.parse("2026-10-07T09:00:00Z")
            val record = GameRecord(
                id = id,
                kind = GameKind.CLASSIC,
                visibility = Visibility.PRIVATE,
                phase = GamePhase.ACTIVE,
                creatorId = alice.user.id,
                whiteId = alice.user.id,
                blackId = computer.id,
                moveCount = 0,
                result = null,
                drawOfferedBy = null,
                botLevel = EngineLevel.EASY,
                createdAt = now,
                updatedAt = now,
                finishedAt = null,
            )
            repositories.games.create(record)
            repositories.games.recordMove(record.copy(moveCount = 1), 0, normal("e2e4"), now)
            return record
        }

        val fetched = interrupted("Restarted001")
        delay(100)
        assertEquals(1, myGames(alice.token).single { it.id == fetched.id }.moveCount, "nothing woke the computer")
        assertEquals(1, game(fetched.id).moveCount)
        assertEquals(2, awaitMoves(alice.token, fetched.id, 2).moveCount)
        assertReplays(game(fetched.id))

        val watched = interrupted("Restarted002")
        client.webSocket("/api/games/${watched.id}/ws") {
            assertEquals(1, assertIs<ServerEvent.GameUpdated>(incoming.nextEvent()).game.moveCount)
            assertEquals(1, assertIs<ServerEvent.Move>(incoming.nextEvent()).ply)
        }
    }

    @Test
    fun noDrawOffersAndNoJoiningButResignationWorks() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val created = computerGame(alice, color = ColorChoice.WHITE)

        draw(alice, created.id, DrawAction.OFFER).assertError(HttpStatusCode.Conflict, "draw_not_available")
        draw(alice, created.id, DrawAction.ACCEPT).assertError(HttpStatusCode.Conflict, "draw_not_available")
        joinResponse(bob, created.id).assertError(HttpStatusCode.Conflict, "game_full")
        moveResponse(bob, created.id, normal("e2e4")).assertError(HttpStatusCode.Forbidden, "not_a_player")

        val resigned = resign(alice, created.id)
        assertEquals(HttpStatusCode.OK, resigned.status)
        val dto = resigned.body<GameDto>()
        assertEquals(GamePhase.FINISHED, dto.status)
        assertEquals(GameResult(Color.BLACK, EndReason.RESIGNATION), dto.result)
        assertNull(game(created.id).drawOfferedBy)
    }

    @Test
    fun computerGamesAreUnlistedButMine() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val computer = createGame(
            alice,
            visibility = Visibility.PUBLIC,
            color = ColorChoice.WHITE,
            opponent = Opponent.COMPUTER,
            level = EngineLevel.HARD,
        )
        assertEquals(Visibility.PRIVATE, computer.visibility)
        val open = createGame(alice)

        suspend fun ids(query: String): List<String> =
            client.get("/api/games$query").body<GamesResponse>().games.map { it.id }
        assertEquals(listOf(open.id), ids(""))
        assertTrue(ids("?filter=active").isEmpty())

        val mine = myGames(alice)
        assertEquals(setOf(open.id, computer.id), mine.map { it.id }.toSet())
        val listed = mine.single { it.id == computer.id }
        assertEquals(EngineLevel.HARD, listed.botLevel)
        assertTrue(listed.black!!.bot)
        assertNull(mine.single { it.id == open.id }.botLevel)
        assertTrue(myGames(bob).isEmpty())

        val json = getGame(computer.id).bodyAsString()
        assertTrue("\"bot\":true" in json, json)
        assertTrue("\"botLevel\":\"HARD\"" in json, json)
        assertEquals(1, Regex("\"bot\":true").findAll(json).count(), "only the computer carries the flag")
    }

    @Test
    fun computerGameRequiresALevel() = serverTest {
        val alice = token("alice")
        val response = createGameResponse(alice, opponent = Opponent.COMPUTER)
        response.assertError(HttpStatusCode.BadRequest, "validation")
        assertTrue(myGames(alice).isEmpty())
        // A level without a computer opponent is ignored.
        assertNull(createGame(alice, level = EngineLevel.EASY).botLevel)
    }

    @Test
    fun computerAccountCannotBeUsed() = serverTest {
        login(BotUser.USERNAME, TEST_PASSWORD).assertError(HttpStatusCode.Unauthorized, "invalid_credentials")
        login(BotUser.USERNAME, "").assertError(HttpStatusCode.Unauthorized, "invalid_credentials")
        register(BotUser.USERNAME).assertError(HttpStatusCode.Conflict, "username_taken")
        register("Computer").assertError(HttpStatusCode.Conflict, "username_taken")
    }
}
