package ru.werelaxe.chess.server

import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.server.model.Rating
import ru.werelaxe.chess.server.model.SeatRatings
import ru.werelaxe.chess.server.service.RatingService
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class RatingServiceTest {
    private val service = RatingService()
    private val at = Instant.parse("2026-10-07T10:00:00Z")

    private fun rating(userId: Long, rating: Int, games: Int = 0) =
        Rating(userId, GameKind.CLASSIC, rating, games, wins = 0, draws = 0, losses = 0, peak = rating, updatedAt = at)

    @Test
    fun kFactorFollowsGamesThenThePeakInFidesOrder() {
        assertEquals(40, service.kFactor(games = 0, peak = 1500))
        assertEquals(40, service.kFactor(games = 29, peak = 1500))
        assertEquals(20, service.kFactor(games = 30, peak = 1500))
        assertEquals(20, service.kFactor(games = 100, peak = 2399))
        assertEquals(10, service.kFactor(games = 100, peak = 2400))
        assertEquals(40, service.kFactor(games = 0, peak = 2400), "the provisional K comes first")
        assertEquals(40, service.kFactor(games = 29, peak = 2600))
    }

    @Test
    fun masterKIsKeptAfterDippingBelow2400() {
        // Peak 2400 reached earlier: K = 10 for good, so a win at 2380 against 2000 adds 1, not 2.
        val master = Rating(1, GameKind.CLASSIC, 2380, games = 200, wins = 0, draws = 0, losses = 0, peak = 2400, updatedAt = at)
        val after = service.update(SeatRatings(master, rating(2, 2000, games = 50)), Color.WHITE, at)
        assertEquals(2381, after.white.rating)
        assertEquals(1998, after.black.rating)
        assertEquals(2400, after.white.peak)
    }

    @Test
    fun expectedScore() {
        assertEquals(0.5, service.expectedScore(1500, 1500))
        assertEquals(0.2403, service.expectedScore(1500, 1700), 0.0001)
        assertEquals(0.7597, service.expectedScore(1700, 1500), 0.0001)
        assertEquals(0.9091, service.expectedScore(2400, 2000), 0.0001)
    }

    @Test
    fun equalPlayersExchangeHalfTheKFactor() {
        val before = SeatRatings(rating(1, 1500), rating(2, 1500))
        val whiteWins = service.update(before, Color.WHITE, at)
        assertEquals(1520, whiteWins.white.rating)
        assertEquals(1480, whiteWins.black.rating)
        assertEquals(Rating(1, GameKind.CLASSIC, 1520, games = 1, wins = 1, draws = 0, losses = 0, peak = 1520, updatedAt = at), whiteWins.white)
        assertEquals(Rating(2, GameKind.CLASSIC, 1480, games = 1, wins = 0, draws = 0, losses = 1, peak = 1500, updatedAt = at), whiteWins.black)

        val blackWins = service.update(before, Color.BLACK, at)
        assertEquals(1480, blackWins.white.rating)
        assertEquals(1520, blackWins.black.rating)

        val draw = service.update(before, null, at)
        assertEquals(1500, draw.white.rating)
        assertEquals(1500, draw.black.rating)
        assertEquals(1, draw.white.draws)
        assertEquals(1, draw.black.draws)
        assertEquals(1, draw.black.games)
    }

    @Test
    fun upsetUsesEachPlayersOwnKFactor() {
        // A newcomer (K = 40) beats an established 1700 player (K = 20).
        val after = service.update(SeatRatings(rating(1, 1500), rating(2, 1700, games = 50)), Color.WHITE, at)
        assertEquals(1530, after.white.rating)
        assertEquals(1685, after.black.rating)
        assertEquals(1700, after.black.peak, "the peak is kept")
    }

    @Test
    fun mastersMoveSlowly() {
        val after = service.update(SeatRatings(rating(1, 2400, games = 200), rating(2, 2000, games = 50)), Color.WHITE, at)
        assertEquals(2401, after.white.rating)
        assertEquals(1998, after.black.rating)
    }

    @Test
    fun roundingIsToTheNearestInteger() {
        // 1480 (1 game) beats 1520 (1 game): 1480 + 40 * (1 - 0.4427) = 1502.29, 1520 - 22.29 = 1497.71.
        val after = service.update(SeatRatings(rating(1, 1480, games = 1), rating(2, 1520, games = 1)), Color.WHITE, at)
        assertEquals(1502, after.white.rating)
        assertEquals(1498, after.black.rating)
    }
}
