package ru.werelaxe.chess.server.service

import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.server.model.Rating
import ru.werelaxe.chess.server.model.SeatRatings
import ru.werelaxe.chess.server.model.User
import java.time.Instant
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Elo with the FIDE-style K factor (docs/ARCHITECTURE.md section 2.8). Pure: it turns the
 * rating rows before a game into the rows after it, and the repositories store them.
 */
class RatingService {
    /**
     * In FIDE's order: 40 while the player has fewer than 30 rated games in the variant, then
     * 10 for good once the player's [peak] rating has reached 2400, and 20 in between.
     */
    fun kFactor(games: Int, peak: Int): Int = when {
        games < PROVISIONAL_GAMES -> 40
        peak >= MASTER_RATING -> 10
        else -> 20
    }

    fun expectedScore(own: Int, opponent: Int): Double = 1.0 / (1.0 + 10.0.pow((opponent - own) / 400.0))

    /** The rating of [own] after scoring [score] (1, 0.5 or 0) against [opponentRating]. */
    fun newRating(own: Rating, opponentRating: Int, score: Double): Int =
        (own.rating + kFactor(own.games, own.peak) * (score - expectedScore(own.rating, opponentRating))).roundToInt()

    /** Both rows after a game that [winner] won (null for a draw), computed from the rows before it. */
    fun update(before: SeatRatings, winner: Color?, at: Instant): SeatRatings {
        val whiteScore = when (winner) {
            Color.WHITE -> 1.0
            Color.BLACK -> 0.0
            null -> 0.5
        }
        return SeatRatings(
            white = before.white.after(before.black.rating, whiteScore, at),
            black = before.black.after(before.white.rating, 1.0 - whiteScore, at),
        )
    }

    private fun Rating.after(opponentRating: Int, score: Double, at: Instant): Rating {
        val updated = newRating(this, opponentRating, score)
        return copy(
            rating = updated,
            games = games + 1,
            wins = wins + if (score == 1.0) 1 else 0,
            draws = draws + if (score == 0.5) 1 else 0,
            losses = losses + if (score == 0.0) 1 else 0,
            peak = max(peak, updated),
            updatedAt = at,
        )
    }

    companion object {
        const val PROVISIONAL_GAMES = 30
        const val MASTER_RATING = 2400
    }
}

/** The rating shown for [user] in [kind]: null for guests, the row's rating or the initial one otherwise. */
fun currentRating(user: User, rows: Collection<Rating>, kind: GameKind): Int? =
    if (user.isGuest) null else rows.firstOrNull { it.userId == user.id && it.kind == kind }?.rating ?: Rating.INITIAL
