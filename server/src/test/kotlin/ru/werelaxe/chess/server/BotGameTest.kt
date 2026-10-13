package ru.werelaxe.chess.server

import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import ru.werelaxe.chess.core.ChessJson
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.server.dto.AuthResponse
import ru.werelaxe.chess.server.dto.BotsResponse
import ru.werelaxe.chess.server.dto.ColorChoice
import ru.werelaxe.chess.server.dto.CreateGameRequest
import ru.werelaxe.chess.server.dto.DrawAction
import ru.werelaxe.chess.server.dto.GameDto
import ru.werelaxe.chess.server.dto.GamesResponse
import ru.werelaxe.chess.server.dto.LeaderboardResponse
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.model.GameRecord
import ru.werelaxe.chess.server.model.GameResult
import ru.werelaxe.chess.server.model.Visibility
import ru.werelaxe.chess.server.repository.Repositories
import ru.werelaxe.chess.server.repository.memory.InMemoryStore
import ru.werelaxe.chess.server.service.BotRoster
import ru.werelaxe.chess.server.service.MoveProvider
import ru.werelaxe.chess.server.ws.ServerEvent
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class BotGameTest {
    private suspend fun ReceiveChannel<Frame>.nextEvent(): ServerEvent = withTimeout(10_000) {
        val frame = assertIs<Frame.Text>(receive())
        ChessJson.json.decodeFromString(ServerEvent.serializer(), frame.readText())
    }

    /** Asserts that the stored moves replay without errors, i.e. every bot move was legal. */
    private fun assertReplays(game: GameDto) {
        assertEquals(game.moveCount, Game.replay(game.kind, game.moves).moveCount)
    }

    @Test
    fun botRepliesToTheHumansMoves() = serverTest {
        val alice = token("alice")
        val created = botGame(alice, color = ColorChoice.WHITE, level = EngineLevel.MEDIUM)
        assertEquals(GamePhase.ACTIVE, created.status)
        assertEquals(Visibility.PUBLIC, created.visibility)
        assertTrue(created.rated)
        assertEquals("alice", created.white?.username)
        val bot = assertNotNull(created.black)
        assertEquals(botNamed(EngineLevel.MEDIUM), bot.username)
        assertTrue(bot.bot)
        assertEquals(EngineLevel.MEDIUM, bot.botLevel)
        assertEquals("Knight Nora", bot.name)
        assertEquals(1500, bot.rating)
        assertFalse(created.white!!.bot)
        assertNull(created.white.botLevel)
        assertEquals(bot, created.invitee)

        move(alice, created.id, "e2e4")
        assertEquals(2, awaitMoves(alice, created.id, 2).moveCount)
        val afterReply = game(created.id)
        assertEquals(GamePhase.ACTIVE, afterReply.status)
        assertIs<GameMove.Normal>(afterReply.moves[1])
        assertReplays(afterReply)

        // 2. d4 is legal whatever black answered, and the bot replies again.
        move(alice, created.id, "d2d4")
        assertEquals(4, awaitMoves(alice, created.id, 4).moveCount)
        assertReplays(game(created.id))
    }

    @Test
    fun botOpensWhenItPlaysWhite() = serverTest {
        val alice = token("alice")
        val created = botGame(alice, color = ColorChoice.BLACK)
        assertEquals(botNamed(EngineLevel.EASY), created.white?.username)
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
    fun botOpensWhenAHumanJoinsItsOpenGame() = serverTest {
        val alice = token("alice")
        val bot = services.bots.roster.first { it.botLevel == EngineLevel.EASY }
        val open = services.games.create(services.bots.principal(bot), CreateGameRequest(GameKind.CLASSIC, color = ColorChoice.WHITE))
        assertEquals(GamePhase.WAITING, open.status)
        assertTrue(open.rated)
        assertEquals(1, client.get("/api/games?filter=open").body<GamesResponse>().games.size)

        val joined = join(alice, open.id)
        assertEquals(GamePhase.ACTIVE, joined.status)
        assertEquals("alice", joined.black?.username)
        assertEquals(1, awaitMoves(alice, open.id, 1).moveCount, "joining wakes the bot")
        move(alice, open.id, "e7e5")
        assertEquals(3, awaitMoves(alice, open.id, 3).moveCount)
    }

    @Test
    fun randomColorSeatsTheBotOpposite() = serverTest {
        val alice = token("alice")
        val seats = (1..8).map { botGame(alice, color = ColorChoice.RANDOM) }.map { game ->
            assertTrue(game.white!!.bot != game.black!!.bot, "exactly one seat is the bot")
            if (game.white.bot) Color.WHITE else Color.BLACK
        }.toSet()
        assertEquals(setOf(Color.WHITE, Color.BLACK), seats)
    }

    @Test
    fun quantumBotMovesAreLegalAndReplayable() = serverTest {
        val alice = token("alice")
        val created = botGame(alice, kind = GameKind.QUANTUM, color = ColorChoice.WHITE)

        move(alice, created.id, split("e2", "e3", "e4"))
        assertEquals(2, awaitMoves(alice, created.id, 2).moveCount)
        val afterReply = game(created.id)
        val reply = afterReply.moves[1]
        if (reply is GameMove.Observe) assertNotNull(reply.outcome, "the server resolves the bot's observations")
        assertReplays(afterReply)

        // d2-d4 cannot be blocked by one black move, so it is legal in every universe.
        move(alice, created.id, "d2d4")
        assertEquals(4, awaitMoves(alice, created.id, 4).moveCount)
        assertReplays(game(created.id))
    }

    /** What the bot player asked a provider for. */
    private data class Think(val gameId: String, val kind: GameKind, val level: EngineLevel, val moves: List<GameMove>)

    @Test
    fun botMovesComeFromTheInjectedProviderWithTheSeatsLevel() {
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
            val created = botGame(alice, kind = GameKind.CLASSIC, color = ColorChoice.WHITE, level = EngineLevel.HARD)
            move(alice, created.id, "e2e4")
            assertEquals(2, awaitMoves(alice, created.id, 2).moveCount)
            assertEquals(listOf(normal("e2e4"), normal("e7e5")), game(created.id).moves)
            assertEquals(listOf(Think(created.id, GameKind.CLASSIC, EngineLevel.HARD, listOf(normal("e2e4")))), thinks)

            move(alice, created.id, "g1f3")
            assertEquals(4, awaitMoves(alice, created.id, 4).moveCount)
            assertEquals(normal("b8c6"), game(created.id).moves[3])
            assertEquals(listOf(normal("e2e4"), normal("e7e5"), normal("g1f3")), thinks.last().moves)
            assertEquals(2, thinks.size, "one request per bot move")
        }
    }

    @Test
    fun botMovesArriveAsMoveEventsAfterAPause() = serverTest(botPause = 400.milliseconds) {
        val alice = token("alice")
        val created = botGame(alice, color = ColorChoice.WHITE)

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
    fun lostBotMoveIsMadeWhenTheGameIsLookedAt() = serverTest {
        val alice = register("alice").body<AuthResponse>()
        val bot = assertNotNull(repositories.users.findByUsername(botNamed(EngineLevel.EASY)))
        assertTrue(bot.isBot)

        // As left behind by a server that stopped before the bot could reply to 1. e4.
        suspend fun interrupted(id: String): GameRecord {
            val now = Instant.parse("2026-10-07T09:00:00Z")
            val record = GameRecord(
                id = id,
                kind = GameKind.CLASSIC,
                visibility = Visibility.PRIVATE,
                phase = GamePhase.ACTIVE,
                creatorId = alice.user.id,
                whiteId = alice.user.id,
                blackId = bot.id,
                inviteeId = bot.id,
                rated = true,
                moveCount = 0,
                result = null,
                drawOfferedBy = null,
                whiteRatingBefore = 1500,
                blackRatingBefore = 1500,
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
        assertEquals(1, myGames(alice.token).single { it.id == fetched.id }.moveCount, "nothing woke the bot")
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
        val created = botGame(alice, color = ColorChoice.WHITE)

        draw(alice, created.id, DrawAction.OFFER).assertError(HttpStatusCode.Conflict, "draw_not_available")
        draw(alice, created.id, DrawAction.ACCEPT).assertError(HttpStatusCode.Conflict, "draw_not_available")
        joinResponse(bob, created.id).assertError(HttpStatusCode.Conflict, "game_full")
        moveResponse(bob, created.id, normal("e2e4")).assertError(HttpStatusCode.Forbidden, "not_a_player")

        val resigned = resign(alice, created.id)
        assertEquals(HttpStatusCode.OK, resigned.status)
        val dto = resigned.body<GameDto>()
        assertEquals(GamePhase.FINISHED, dto.status)
        assertEquals(GameResult(Color.BLACK, EndReason.RESIGNATION), dto.result)
        assertNull(dto.ratingChanges, "nobody moved, so nothing is rated")
        assertNull(game(created.id).drawOfferedBy)
    }

    @Test
    fun botGamesAreListedLikeAnyOther() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val public = botGame(alice, visibility = Visibility.PUBLIC, color = ColorChoice.WHITE, level = EngineLevel.HARD)
        val private = botGame(alice, visibility = Visibility.PRIVATE, color = ColorChoice.WHITE)
        val open = createGame(alice)

        suspend fun ids(query: String): List<String> =
            client.get("/api/games$query").body<GamesResponse>().games.map { it.id }
        assertEquals(setOf(open.id, public.id), ids("").toSet())
        assertEquals(listOf(public.id), ids("?filter=active"))
        assertEquals(listOf(open.id), ids("?filter=open"))

        val mine = myGames(alice)
        assertEquals(setOf(open.id, public.id, private.id), mine.map { it.id }.toSet())
        val listed = mine.single { it.id == public.id }
        assertEquals(EngineLevel.HARD, listed.black?.botLevel)
        assertTrue(listed.black!!.bot)
        assertNull(mine.single { it.id == open.id }.black)
        assertTrue(myGames(bob).isEmpty())

        val json = getGame(public.id).bodyAsString()
        assertTrue("\"bot\":true" in json, json)
        assertTrue("\"botLevel\":\"HARD\"" in json, json)
        assertEquals(2, Regex("\"bot\":true").findAll(json).count(), "the bot appears as black and as the invitee")
    }

    @Test
    fun botAccountsCannotBeUsed() = serverTest {
        val bot = botNamed(EngineLevel.EASY)
        login(bot, TEST_PASSWORD).assertError(HttpStatusCode.Unauthorized, "invalid_credentials")
        login(bot, "").assertError(HttpStatusCode.Unauthorized, "invalid_credentials")
        register(bot).assertError(HttpStatusCode.Conflict, "username_taken")
        register(bot.uppercase()).assertError(HttpStatusCode.Conflict, "username_taken")
        register(BotRoster.COMPUTER).assertError(HttpStatusCode.Conflict, "username_taken")
        register("Computer").assertError(HttpStatusCode.Conflict, "username_taken")
    }

    @Test
    fun legacyComputerAccountStillPlays() = serverTest {
        val alice = token("alice")
        val created = invite(alice, BotRoster.COMPUTER, color = ColorChoice.WHITE)
        assertEquals(GamePhase.ACTIVE, created.status)
        val computer = assertNotNull(created.black)
        assertTrue(computer.bot)
        assertEquals("Computer", computer.name)
        assertEquals(EngineLevel.MEDIUM, computer.botLevel)

        move(alice, created.id, "e2e4")
        assertEquals(2, awaitMoves(alice, created.id, 2).moveCount)
        assertReplays(game(created.id))
    }

    @Test
    fun botGamesAreRatedByDefaultAndCountWhenFinished() = serverTest {
        val alice = token("alice")
        val bot = botNamed(EngineLevel.EASY)
        val created = botGame(alice, color = ColorChoice.WHITE)
        move(alice, created.id, "e2e4")
        awaitMoves(alice, created.id, 2)

        val resigned = resign(alice, created.id).body<GameDto>()
        assertEquals(GameResult(Color.BLACK, EndReason.RESIGNATION), resigned.result)
        val changes = assertNotNull(resigned.ratingChanges)
        assertEquals(1500, changes.white.before)
        assertEquals(1480, changes.white.after)
        assertEquals(1520, changes.black.after)
        assertEquals(1520, profile(bot).ratings.classic?.rating)
        val entries = client.get("/api/players/leaderboard?kind=CLASSIC").body<LeaderboardResponse>().entries
        assertEquals(listOf(bot, "alice"), entries.map { it.user.username })

        val casual = botGame(alice, color = ColorChoice.WHITE, rated = false)
        assertFalse(casual.rated)
    }

    /**
     * Stores a game as left behind by a server that stopped before the bot ([blackId]) could
     * reply to 1. e4 by [whiteId]; nobody is woken.
     */
    private suspend fun Repositories.interrupted(id: String, whiteId: Long, blackId: Long): GameRecord {
        val now = Instant.parse("2026-10-07T09:00:00Z")
        val record = GameRecord(
            id = id,
            kind = GameKind.CLASSIC,
            visibility = Visibility.PUBLIC,
            phase = GamePhase.ACTIVE,
            creatorId = whiteId,
            whiteId = whiteId,
            blackId = blackId,
            inviteeId = null,
            rated = true,
            moveCount = 0,
            result = null,
            drawOfferedBy = null,
            whiteRatingBefore = 1500,
            blackRatingBefore = 1500,
            createdAt = now,
            updatedAt = now,
            finishedAt = null,
        )
        games.create(record)
        games.recordMove(record.copy(moveCount = 1), 0, normal("e2e4"), now)
        return record.copy(moveCount = 1)
    }

    @Test
    fun stalledBotGamesAreWokenAtStartup() {
        // Rows that exist before the application starts: a person, a bot and their interrupted game.
        val repositories = InMemoryStore().repositories()
        val id = "Restarted003"
        runBlocking {
            val alice = assertNotNull(repositories.users.create("alice", "unused-hash", TEST_START))
            val bot = assertNotNull(repositories.users.upsertBot(botNamed(EngineLevel.EASY), "Pawn Pal", EngineLevel.EASY, TEST_START))
            repositories.interrupted(id, alice.id, bot.id)
        }
        serverTest(repositories = repositories) {
            // Nothing but the start-up (and the health check, which wakes nobody) happened.
            assertEquals(2, awaitStoredMoves(id, 2), "the bot's reply was made at startup")
            assertReplays(game(id))
        }
    }

    @Test
    fun stalledBotGamesAreWokenByTheMatchmakingTick() = serverTest {
        val alice = register("alice").body<AuthResponse>()
        val bot = assertNotNull(repositories.users.findByUsername(botNamed(EngineLevel.EASY)))
        val stalled = repositories.interrupted("Restarted004", alice.user.id, bot.id)
        val humanOnTurn = repositories.interrupted("Restarted005", bot.id, alice.user.id)
        delay(100)
        assertEquals(1, awaitStoredMoves(stalled.id, 1), "nothing woke the bot")

        services.matchmaker.tick()
        assertEquals(2, awaitStoredMoves(stalled.id, 2), "the tick woke the bot that was on turn")
        assertReplays(game(stalled.id))
        delay(100)
        assertEquals(1, assertNotNull(repositories.games.findById(humanOnTurn.id)).moveCount, "a game with a person on turn is left alone")
        assertEquals(0, services.games.wakeBots(), "nothing is on a bot's turn any more")
    }

    private class EngineCrash : Error("the engine blew up")

    @Test
    fun slotIsReleasedWhenTheSearchThrowsAnError() {
        val searches = AtomicInteger()
        val crashing = object : MoveProvider {
            override suspend fun chooseMove(gameId: String, kind: GameKind, level: EngineLevel, moves: List<GameMove>): GameMove? {
                if (searches.getAndIncrement() == 0) throw EngineCrash()
                return normal("e7e5")
            }
        }
        serverTest(moveProvider = crashing) {
            val alice = token("alice")
            val created = botGame(alice, color = ColorChoice.WHITE)
            move(alice, created.id, "e2e4")
            withTimeout(5_000) {
                while (searches.get() < 1) delay(10)
            }
            // The crashed search must have freed the game: every look at it wakes the bot again,
            // and a leaked slot would swallow those wake-ups until a restart.
            withTimeout(10_000) {
                while (game(created.id).moveCount < 2) delay(50)
            }
            assertEquals(listOf(normal("e2e4"), normal("e7e5")), game(created.id).moves)
            assertEquals(2, searches.get(), "the crash and the one successful search")
        }
    }

    @Test
    fun rosterNameHeldByAPersonIsSkippedNotFatal() {
        val repositories = InMemoryStore().repositories()
        val taken = botNamed(EngineLevel.MEDIUM)
        runBlocking { assertNotNull(repositories.users.create(taken, "unused-hash", TEST_START)) }
        serverTest(repositories = repositories) {
            val bots = client.get("/api/bots").body<BotsResponse>().bots
            assertEquals(BotRoster.specs.count { !it.legacy } - 1, bots.size, "the server starts with the rest of the roster")
            assertTrue(bots.none { it.user.username == taken })
            assertEquals(bots.map { it.user.username }, services.bots.roster.map { it.username })
            val person = assertNotNull(repositories.users.findByUsername(taken))
            assertFalse(person.isBot, "the account is left as it is")

            // The person is invited like any human; the other bot of that level still plays.
            val alice = token("alice")
            val invitation = invite(alice, taken)
            assertEquals(GamePhase.WAITING, invitation.status)
            assertFalse(assertNotNull(invitation.invitee).bot)
            val other = BotRoster.specs.first { it.level == EngineLevel.MEDIUM && it.username != taken }
            assertEquals(GamePhase.ACTIVE, invite(alice, other.username).status)
        }
    }
}
