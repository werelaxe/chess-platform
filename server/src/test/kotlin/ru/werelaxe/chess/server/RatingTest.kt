package ru.werelaxe.chess.server

import io.ktor.client.call.body
import io.ktor.client.request.get
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.server.dto.ColorChoice
import ru.werelaxe.chess.server.dto.DrawAction
import ru.werelaxe.chess.server.dto.GameDto
import ru.werelaxe.chess.server.dto.GameRatingChanges
import ru.werelaxe.chess.server.dto.RatingDelta
import ru.werelaxe.chess.server.dto.RatingSummary
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.model.GameResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RatingTest {
    @Test
    fun checkmateInARatedGameUpdatesBothRatings() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val game = activeGame(alice, bob)
        assertTrue(game.rated)
        assertEquals(1500, game.white?.rating)
        assertEquals(1500, game.black?.rating)
        assertNull(game.ratingChanges)

        foolsMate(alice, bob, game.id)
        val finished = game(game.id)
        assertEquals(GameResult(Color.BLACK, EndReason.CHECKMATE), finished.result)
        assertEquals(GameRatingChanges(RatingDelta(1500, 1480), RatingDelta(1500, 1520)), finished.ratingChanges)
        assertEquals(1480, finished.white?.rating, "the reference carries the current rating")
        assertEquals(1520, finished.black?.rating)

        assertEquals(RatingSummary(1480, games = 1, wins = 0, draws = 0, losses = 1, peak = 1500), profile("alice").ratings.classic)
        assertEquals(RatingSummary(1520, games = 1, wins = 1, draws = 0, losses = 0, peak = 1520), profile("bob").ratings.classic)
        assertNull(profile("alice").ratings.quantum, "the variants are rated separately")
        assertEquals(1480, me(alice).ratings.classic?.rating)

        val changes = repositories.ratings.changesFor(game.id)
        assertEquals(2, changes.size)
        assertEquals(setOf(1500), changes.map { it.before }.toSet())
    }

    @Test
    fun resignationAndDrawAgreementAreRated() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val resigned = activeGame(alice, bob)
        move(alice, resigned.id, "e2e4")
        move(bob, resigned.id, "e7e5")
        val afterResign = resign(bob, resigned.id).body<GameDto>()
        assertEquals(GameRatingChanges(RatingDelta(1500, 1520), RatingDelta(1500, 1480)), afterResign.ratingChanges)

        // Now 1520 against 1480 with one game each: the favourite gains less than the half K.
        val drawn = activeGame(alice, bob)
        assertEquals(1520, drawn.white?.rating)
        assertEquals(1480, drawn.black?.rating)
        move(alice, drawn.id, "d2d4")
        move(bob, drawn.id, "d7d5")
        assertEquals(200, draw(alice, drawn.id, DrawAction.OFFER).status.value)
        val afterDraw = draw(bob, drawn.id, DrawAction.ACCEPT).body<GameDto>()
        assertEquals(GamePhase.FINISHED, afterDraw.status)
        val changes = assertNotNull(afterDraw.ratingChanges)
        // E(1520 vs 1480) = 0.5573: the draw costs the favourite 40 * 0.0573 = 2.29 points.
        assertEquals(RatingDelta(1520, 1518), changes.white)
        assertEquals(RatingDelta(1480, 1482), changes.black)
        assertEquals(RatingSummary(1518, games = 2, wins = 1, draws = 1, losses = 0, peak = 1520), profile("alice").ratings.classic)
    }

    @Test
    fun unratedGameChangesNothing() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val game = activeGame(alice, bob, rated = false)
        assertFalse(game.rated)
        foolsMate(alice, bob, game.id)
        val finished = game(game.id)
        assertEquals(GamePhase.FINISHED, finished.status)
        assertNull(finished.ratingChanges)
        assertNull(profile("alice").ratings.classic)
        assertNull(profile("bob").ratings.classic)
        assertTrue(repositories.ratings.changesFor(game.id).isEmpty())
    }

    @Test
    fun guestsOnlyPlayUnratedGames() = serverTest {
        val alice = token("alice")
        val guest = guest()
        val hosted = createGame(guest.token, rated = true)
        assertFalse(hosted.rated, "a guest's own game is never rated")
        assertNull(hosted.white?.rating)

        val visited = createGame(alice, rated = true)
        assertTrue(visited.rated)
        val joined = join(guest.token, visited.id)
        assertFalse(joined.rated, "a guest joining drops the flag")
        assertEquals(1500, joined.white?.rating)
        assertNull(joined.black?.rating)
        move(alice, visited.id, "e2e4")
        move(guest.token, visited.id, "e7e5")
        assertNull(resign(guest.token, visited.id).body<GameDto>().ratingChanges)
        assertNull(profile("alice").ratings.classic)

        val record = assertNotNull(repositories.games.findById(visited.id))
        assertEquals(1500, record.whiteRatingBefore)
        assertNull(record.blackRatingBefore)
    }

    @Test
    fun aGameOverBeforeBothMovedIsNotRated() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val untouched = activeGame(alice, bob)
        assertNull(resign(alice, untouched.id).body<GameDto>().ratingChanges)

        val oneMove = activeGame(alice, bob)
        move(alice, oneMove.id, "e2e4")
        val finished = resign(alice, oneMove.id).body<GameDto>()
        assertEquals(GamePhase.FINISHED, finished.status)
        assertTrue(finished.rated, "the flag stays; the result simply does not count")
        assertNull(finished.ratingChanges)
        assertNull(profile("alice").ratings.classic)
        assertNull(profile("bob").ratings.classic)
    }

    @Test
    fun ratingSnapshotsAreTakenWhenTheGameStarts() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val first = activeGame(alice, bob)
        foolsMate(alice, bob, first.id)

        val created = createGame(bob, kind = GameKind.QUANTUM, color = ColorChoice.BLACK)
        assertNull(assertNotNull(repositories.games.findById(created.id)).blackRatingBefore, "nothing is snapshotted while waiting")
        join(alice, created.id)
        val quantum = assertNotNull(repositories.games.findById(created.id))
        assertEquals(1500, quantum.whiteRatingBefore, "the classic result does not touch the quantum rating")
        assertEquals(1500, quantum.blackRatingBefore)

        val classic = activeGame(alice, bob)
        val record = assertNotNull(repositories.games.findById(classic.id))
        assertEquals(1480, record.whiteRatingBefore)
        assertEquals(1520, record.blackRatingBefore)
    }

    @Test
    fun ratedFlagIsSerialized() = serverTest {
        val alice = token("alice")
        val game = createGame(alice, rated = false)
        val json = client.get("/api/games/${game.id}").bodyAsString()
        assertTrue("\"rated\":false" in json, json)
        assertTrue("\"ratingChanges\":null" in json, json)
    }
}
