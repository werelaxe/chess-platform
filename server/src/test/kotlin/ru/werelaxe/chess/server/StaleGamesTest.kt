package ru.werelaxe.chess.server

import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeout
import ru.werelaxe.chess.core.ChessJson
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.engine.ThinkPriority
import ru.werelaxe.chess.server.dto.ColorChoice
import ru.werelaxe.chess.server.dto.DrawAction
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A game repository whose stale listing can be scripted with records that changed since, as
 * the real one returns when a move arrives between the sweep's listing and its abort.
 */
private class StaleSweepListings(private val delegate: GameRepository) : GameRepository by delegate {
    var staleGames: List<GameRecord>? = null

    override suspend fun listActiveGamesUpdatedBefore(before: Instant, limit: Int): List<GameRecord> =
        staleGames ?: delegate.listActiveGamesUpdatedBefore(before, limit)
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

    @Test
    fun finishesGamesNobodyTouchedForTheThresholdWithoutRatingChanges() = serverTest(moveProvider = idleBots) {
        val alice = token("alice")
        val bob = token("bob")
        // Both players have ratings from a finished game, so that a change would show.
        foolsMate(alice, bob, activeGame(alice, bob).id)
        val aliceRatings = profile("alice").ratings
        val bobRatings = profile("bob").ratings

        val stale = activeGame(alice, bob)
        move(alice, stale.id, "e2e4")
        move(bob, stale.id, "e7e5")
        val withBot = botGame(alice, color = ColorChoice.WHITE)
        clock.advance(staleAge.plusMinutes(1))

        client.webSocket("/api/games/${stale.id}/ws") {
            assertEquals(GamePhase.ACTIVE, assertIs<ServerEvent.GameUpdated>(incoming.nextEvent()).game.status)
            assertEquals(2, services.staleGames.sweep(), "the game between people and the game against the bot")
            val finished = assertIs<ServerEvent.GameUpdated>(incoming.nextEvent()).game
            assertEquals(GamePhase.FINISHED, finished.status, "the subscribers see the game end")
            assertEquals(GameResult(null, EndReason.ABANDONMENT), finished.result)
            assertEquals(2, finished.moves.size)
            assertNull(finished.ratingChanges)
        }
        val stored = game(stale.id)
        assertEquals(GamePhase.FINISHED, stored.status)
        assertEquals(GameResult(null, EndReason.ABANDONMENT), stored.result)
        assertEquals(GamePhase.FINISHED, game(withBot.id).status)
        assertTrue(repositories.ratings.changesFor(stale.id).isEmpty())
        assertEquals(aliceRatings, profile("alice").ratings, "no rating change for either player")
        assertEquals(bobRatings, profile("bob").ratings)
        moveResponse(alice, stale.id, normal("g1f3")).assertError(HttpStatusCode.Conflict, "game_finished")
        assertEquals(0, services.staleGames.sweep(), "nothing is left to finish")
    }

    @Test
    fun countsTheIdleTimeFromTheLastMoveOrDrawOffer() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val played = activeGame(alice, bob)
        clock.advance(staleAge.minusMinutes(1))
        assertEquals(0, services.staleGames.sweep(), "idle for a minute less than the threshold")
        assertEquals(GamePhase.ACTIVE, game(played.id).status)

        move(alice, played.id, "e2e4")
        clock.advance(staleAge.minusMinutes(1))
        assertEquals(0, services.staleGames.sweep(), "the move restarted the count")
        assertEquals(HttpStatusCode.OK, draw(bob, played.id, DrawAction.OFFER).status)
        clock.advance(staleAge.minusMinutes(1))
        assertEquals(0, services.staleGames.sweep(), "so did the draw offer")

        clock.advance(Duration.ofMinutes(2))
        assertEquals(1, services.staleGames.sweep())
        val finished = game(played.id)
        assertEquals(GamePhase.FINISHED, finished.status)
        assertEquals(GameResult(null, EndReason.ABANDONMENT), finished.result)
        assertNull(finished.drawOfferedBy, "the pending offer goes with the game")
        assertEquals(1, finished.moves.size)
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
