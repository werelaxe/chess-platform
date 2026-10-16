package ru.werelaxe.chess.server

import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeout
import ru.werelaxe.chess.core.ChessJson
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.engine.ThinkPriority
import ru.werelaxe.chess.server.dto.ColorChoice
import ru.werelaxe.chess.server.dto.CreateGameRequest
import ru.werelaxe.chess.server.dto.DrawAction
import ru.werelaxe.chess.server.dto.GameDto
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.model.GameRecord
import ru.werelaxe.chess.server.model.GameResult
import ru.werelaxe.chess.server.repository.GameRepository
import ru.werelaxe.chess.server.repository.memory.InMemoryStore
import ru.werelaxe.chess.server.service.MoveProvider
import ru.werelaxe.chess.server.ws.ServerEvent
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A game repository whose stale listing can be scripted with records that changed since, as
 * the real one returns when a move arrives between the sweep's listing and its forfeit.
 */
private class StaleSweepListings(private val delegate: GameRepository) : GameRepository by delegate {
    var staleGames: List<GameRecord>? = null

    override suspend fun listActiveGamesLastMovedBefore(before: Instant, limit: Int): List<GameRecord> =
        staleGames ?: delegate.listActiveGamesLastMovedBefore(before, limit)
}

class StaleGamesTest {
    /** Bots that never move, so that the games stay as the tests left them. */
    private val idleBots = object : MoveProvider {
        override suspend fun chooseMove(
            gameId: String,
            kind: GameKind,
            level: EngineLevel,
            moves: List<GameMove>,
            priority: ThinkPriority,
        ): GameMove? = null
    }

    /** The configured threshold: 24 hours unless a test says otherwise. */
    private val staleAge: Duration = Duration.ofHours(TEST_CONFIG.gameStaleHours.toLong())

    private suspend fun ReceiveChannel<Frame>.nextEvent(): ServerEvent = withTimeout(5_000) {
        val frame = assertIs<Frame.Text>(receive())
        ChessJson.json.decodeFromString(ServerEvent.serializer(), frame.readText())
    }

    /** Makes a move for the bot seated as [color] in [game]; the idle bots never move on their own. */
    private suspend fun TestContext.botMove(game: GameDto, color: Color, uci: String) {
        val seat = assertNotNull(if (color == Color.WHITE) game.white else game.black)
        val bot = checkNotNull(services.bots[seat.id]) { "${seat.username} is not a bot" }
        services.games.move(services.bots.principal(bot), game.id, normal(uci))
    }

    /** An ACTIVE game between two roster bots, seated the way the matchmaker seats them. */
    private suspend fun TestContext.botVersusBotGame(): GameDto {
        val (white, black) = services.bots.roster.takeLast(2)
        val created = services.games.create(
            services.bots.principal(white),
            CreateGameRequest(GameKind.CLASSIC, color = ColorChoice.WHITE),
        )
        return services.games.join(services.bots.principal(black), created.id)
    }

    /** The stored classic rating of [userId], or null before the first rated result. */
    private suspend fun TestContext.classicRating(userId: Long): Int? =
        repositories.ratings.forUsers(listOf(userId)).firstOrNull { it.kind == GameKind.CLASSIC }?.rating

    @Test
    fun theHumanOnTurnForfeitsARatedGameAgainstABot() = serverTest(moveProvider = idleBots) {
        val alice = token("alice")
        val aliceId = me(alice).id
        val game = botGame(alice, color = ColorChoice.WHITE)
        val bot = assertNotNull(game.black)
        move(alice, game.id, "e2e4")
        botMove(game, Color.BLACK, "e7e5")
        clock.advance(staleAge.plusMinutes(1))

        var published: GameDto? = null
        client.webSocket("/api/games/${game.id}/ws") {
            assertEquals(GamePhase.ACTIVE, assertIs<ServerEvent.GameUpdated>(incoming.nextEvent()).game.status)
            assertEquals(1, services.staleGames.sweep())
            published = assertIs<ServerEvent.GameUpdated>(incoming.nextEvent()).game
        }
        val finished = assertNotNull(published, "the subscribers see the game end")
        assertEquals(GamePhase.FINISHED, finished.status)
        assertEquals(GameResult(Color.BLACK, EndReason.ABANDONMENT), finished.result, "white was on turn and loses")
        assertEquals(2, finished.moves.size)
        val changes = assertNotNull(finished.ratingChanges, "rated and both sides moved: the result counts")
        assertTrue(changes.white.after < changes.white.before, "the human's rating drops: $changes")
        assertTrue(changes.black.after > changes.black.before, "the bot's rating rises: $changes")
        assertEquals(changes.white.after, classicRating(aliceId))
        assertEquals(changes.black.after, classicRating(bot.id))
        assertEquals(2, repositories.ratings.changesFor(game.id).size, "one recorded change per seat")

        val stored = game(game.id)
        assertEquals(GameResult(Color.BLACK, EndReason.ABANDONMENT), stored.result)
        assertEquals(changes, stored.ratingChanges)
        assertEquals(1, assertNotNull(profile("alice").ratings.classic).losses)
        moveResponse(alice, game.id, normal("g1f3")).assertError(HttpStatusCode.Conflict, "game_finished")
        assertEquals(0, services.staleGames.sweep(), "nothing is left to finish")
    }

    @Test
    fun forfeitsAnUnratedGameWithoutTouchingTheRatings() = serverTest(moveProvider = idleBots) {
        val alice = token("alice")
        val casual = botGame(alice, color = ColorChoice.WHITE, rated = false)
        val bot = assertNotNull(casual.black)
        move(alice, casual.id, "e2e4")
        botMove(casual, Color.BLACK, "e7e5")
        // A guest is a human too, and a guest's game is never rated.
        val guest = guest().token
        val guests = invite(guest, bot.username, color = ColorChoice.WHITE)
        assertFalse(guests.rated)
        move(guest, guests.id, "d2d4")
        botMove(guests, Color.BLACK, "d7d5")
        clock.advance(staleAge.plusMinutes(1))

        assertEquals(2, services.staleGames.sweep(), "both games go in one sweep")
        for (id in listOf(casual.id, guests.id)) {
            val finished = game(id)
            assertEquals(GamePhase.FINISHED, finished.status)
            assertEquals(GameResult(Color.BLACK, EndReason.ABANDONMENT), finished.result, "the human on turn loses all the same")
            assertNull(finished.ratingChanges)
            assertTrue(repositories.ratings.changesFor(id).isEmpty())
            assertEquals(bot.rating, finished.black?.rating, "the bot's rating is as it was")
        }
        assertNull(profile("alice").ratings.classic, "no rated game was played")
        assertNull(classicRating(bot.id))
    }

    @Test
    fun theSideToMoveForfeitsAGameBetweenPeople() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val stale = activeGame(alice, bob)
        move(alice, stale.id, "e2e4")
        move(bob, stale.id, "e7e5")
        move(alice, stale.id, "g1f3")
        // The same game between two other players, which the side to move resigns instead.
        val carol = token("carol")
        val dave = token("dave")
        val resigned = activeGame(carol, dave)
        move(carol, resigned.id, "e2e4")
        move(dave, resigned.id, "e7e5")
        move(carol, resigned.id, "g1f3")
        assertEquals(HttpStatusCode.OK, resign(dave, resigned.id).status)
        clock.advance(staleAge.plusMinutes(1))

        assertEquals(1, services.staleGames.sweep())
        val finished = game(stale.id)
        assertEquals(GamePhase.FINISHED, finished.status)
        assertEquals(GameResult(Color.WHITE, EndReason.ABANDONMENT), finished.result, "black was on turn and loses")
        val changes = assertNotNull(finished.ratingChanges)
        assertTrue(changes.white.after > changes.white.before, "$changes")
        assertTrue(changes.black.after < changes.black.before, "$changes")
        assertEquals(game(resigned.id).ratingChanges, changes, "the same update as a resignation by the side to move")
        assertEquals(1, assertNotNull(profile("alice").ratings.classic).wins)
        assertEquals(1, assertNotNull(profile("bob").ratings.classic).losses)
        assertEquals(GameResult(Color.WHITE, EndReason.RESIGNATION), game(resigned.id).result, "a resignation stays a resignation")
    }

    @Test
    fun endsAGameWithABotOnTurnWithoutAWinnerOrRatingChange() = serverTest(moveProvider = idleBots) {
        val alice = token("alice")
        val game = botGame(alice, color = ColorChoice.WHITE)
        val bot = assertNotNull(game.black)
        move(alice, game.id, "e2e4")
        botMove(game, Color.BLACK, "e7e5")
        move(alice, game.id, "g1f3")
        clock.advance(staleAge.plusMinutes(1))

        assertEquals(1, services.staleGames.sweep())
        val finished = game(game.id)
        assertEquals(GamePhase.FINISHED, finished.status)
        assertEquals(GameResult(null, EndReason.ABANDONMENT), finished.result, "the bot on turn did not walk away: no forfeit")
        assertEquals(3, finished.moves.size)
        assertTrue(finished.rated)
        assertNull(finished.ratingChanges, "the human does not gain rating from a stalled bot")
        assertTrue(repositories.ratings.changesFor(game.id).isEmpty())
        assertEquals(bot.rating, finished.black?.rating)
        assertNull(profile("alice").ratings.classic)
        assertNull(classicRating(bot.id))
    }

    @Test
    fun finishesAGameBetweenTwoBotsWithoutAWinnerOrRatingChange() = serverTest(moveProvider = idleBots) {
        val game = botVersusBotGame()
        val white = assertNotNull(game.white)
        val black = assertNotNull(game.black)
        assertTrue(white.bot && black.bot && game.rated)
        botMove(game, Color.WHITE, "e2e4")
        botMove(game, Color.BLACK, "e7e5")
        clock.advance(staleAge.plusMinutes(1))

        assertEquals(1, services.staleGames.sweep())
        val finished = game(game.id)
        assertEquals(GamePhase.FINISHED, finished.status)
        assertEquals(GameResult(null, EndReason.ABANDONMENT), finished.result, "nobody walked away: the system held the bots")
        assertEquals(2, finished.moves.size)
        assertNull(finished.ratingChanges, "rated with both sides moved, yet no rating change")
        assertTrue(repositories.ratings.changesFor(game.id).isEmpty())
        assertEquals(white.rating, finished.white?.rating)
        assertEquals(black.rating, finished.black?.rating)
        assertNull(classicRating(white.id))
        assertNull(classicRating(black.id))
    }

    @Test
    fun countsTheIdleTimeFromTheLastMoveAndNotFromADrawOffer() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val played = activeGame(alice, bob)
        clock.advance(staleAge.minusMinutes(1))
        assertEquals(0, services.staleGames.sweep(), "idle for a minute less than the threshold")
        assertEquals(GamePhase.ACTIVE, game(played.id).status)

        move(alice, played.id, "e2e4")
        clock.advance(staleAge.minusMinutes(1))
        assertEquals(0, services.staleGames.sweep(), "the move restarted the count")
        // Bob, on turn, tries to keep the game alive without moving: an offer, withdrawn and made again.
        assertEquals(HttpStatusCode.OK, draw(bob, played.id, DrawAction.OFFER).status)
        assertEquals(HttpStatusCode.OK, draw(bob, played.id, DrawAction.WITHDRAW).status)
        assertEquals(HttpStatusCode.OK, draw(bob, played.id, DrawAction.OFFER).status)
        assertEquals(0, services.staleGames.sweep(), "still a minute short of the threshold")
        assertTrue(Instant.parse(game(played.id).updatedAt) > Instant.parse(played.updatedAt), "the offers touched the record")

        clock.advance(Duration.ofMinutes(2))
        assertEquals(1, services.staleGames.sweep(), "the draw offers did not restart the count")
        val finished = game(played.id)
        assertEquals(GamePhase.FINISHED, finished.status)
        assertEquals(GameResult(Color.WHITE, EndReason.ABANDONMENT), finished.result, "black was on turn")
        assertNull(finished.drawOfferedBy, "the pending offer goes with the game")
        assertEquals(1, finished.moves.size)
        assertNull(finished.ratingChanges, "black never moved: the result does not count")
        assertTrue(repositories.ratings.changesFor(played.id).isEmpty())
    }

    @Test
    fun leavesWaitingAndFinishedGamesAlone() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val waiting = createGame(alice)
        val finished = activeGame(alice, bob)
        foolsMate(alice, bob, finished.id)
        val result = game(finished.id)
        clock.advance(staleAge.multipliedBy(10))

        assertEquals(0, services.staleGames.sweep())
        assertEquals(GamePhase.WAITING, game(waiting.id).status)
        assertEquals(result, game(finished.id), "a finished game keeps its result")
    }

    @Test
    fun zeroSwitchesTheSweepOff() = serverTest(config = TEST_CONFIG.copy(gameStaleHours = 0)) {
        val alice = token("alice")
        val bob = token("bob")
        val played = activeGame(alice, bob)
        clock.advance(Duration.ofDays(30))
        assertEquals(0, services.staleGames.sweep())
        assertEquals(GamePhase.ACTIVE, game(played.id).status)

        // The age is read from the settings before every sweep.
        services.settings.set("game.stale_hours", "1")
        assertEquals(1, services.staleGames.sweep())
        assertEquals(GamePhase.FINISHED, game(played.id).status)
    }

    @Test
    fun leavesAGameThatGotAMoveSinceTheListing() {
        val store = InMemoryStore().repositories()
        val listings = StaleSweepListings(store.games)
        serverTest(repositories = store.copy(games = listings)) {
            val alice = token("alice")
            val bob = token("bob")
            val played = activeGame(alice, bob)
            clock.advance(staleAge.plusMinutes(1))
            // The listing shows the game as it was before the move that arrives right after it.
            listings.staleGames = listOf(checkNotNull(store.games.findById(played.id)))
            move(alice, played.id, "e2e4")
            assertEquals(0, services.staleGames.sweep(), "checked again under the game's lock")
            val alive = game(played.id)
            assertEquals(GamePhase.ACTIVE, alive.status)
            assertEquals(1, alive.moves.size)

            listings.staleGames = null
            clock.advance(staleAge.plusMinutes(1))
            assertEquals(1, services.staleGames.sweep())
            assertEquals(GamePhase.FINISHED, game(played.id).status)
        }
    }
}
