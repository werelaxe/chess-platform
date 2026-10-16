package ru.werelaxe.chess.server.model

import kotlinx.serialization.Serializable
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.engine.EngineLevel
import java.time.Instant

@Serializable
enum class Visibility {
    PUBLIC,
    PRIVATE,
}

/** Lifecycle state of a game row (`games.status`). */
@Serializable
enum class GamePhase {
    WAITING,
    ACTIVE,
    FINISHED,
}

/** [winner] is null for a draw. */
@Serializable
data class GameResult(val winner: Color?, val reason: EndReason)

/**
 * [passwordHash] is null for guests, who cannot log in and exist only through their token, and
 * for bots ([isBot]), which never log in at all. [locale] is the UI language the user chose
 * (`en` or `ru`), null until they pick one; [displayName] is the optional name shown next to
 * the username. [botLevel] is the strength of a bot and null for people. [lastSeenAt] is the
 * time of the last authenticated request, refreshed every few minutes at most.
 */
data class User(
    val id: Long,
    val username: String,
    val passwordHash: String?,
    val isGuest: Boolean,
    val isBot: Boolean,
    val createdAt: Instant,
    val locale: String?,
    val displayName: String?,
    val botLevel: EngineLevel?,
    val lastSeenAt: Instant?,
)

/** The fields of the own profile that `PATCH /api/auth/me` can change. */
enum class ProfileField {
    LOCALE,
    DISPLAY_NAME,
}

/**
 * A row of the `games` table; the moves live separately and are the only game state.
 * [inviteeId] is set for an invitation: the only account allowed to join it. [rated] is
 * whether the result counts for the ratings (forced off by a guest seat); the
 * `ratingBefore` snapshots are taken when the game becomes ACTIVE. [lastMoveAt] is when the
 * game was last played: the latest move, or the start of the game before the first one (null
 * while WAITING); the stale sweep counts idle time from here, so a draw offer, which touches
 * [updatedAt] like any change, does not keep a game alive.
 */
data class GameRecord(
    val id: String,
    val kind: GameKind,
    val visibility: Visibility,
    val phase: GamePhase,
    val creatorId: Long,
    val whiteId: Long?,
    val blackId: Long?,
    val inviteeId: Long?,
    val rated: Boolean,
    val moveCount: Int,
    val result: GameResult?,
    val drawOfferedBy: Color?,
    val whiteRatingBefore: Int?,
    val blackRatingBefore: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastMoveAt: Instant?,
    val finishedAt: Instant?,
) {
    fun colorOf(userId: Long): Color? = when {
        whiteId != null && whiteId == userId -> Color.WHITE
        blackId != null && blackId == userId -> Color.BLACK
        else -> null
    }

    fun seat(color: Color): Long? = if (color == Color.WHITE) whiteId else blackId

    fun involves(userId: Long): Boolean = creatorId == userId || colorOf(userId) != null

    /** Whose turn it is, judged from the move count alone: every move of either variant passes the turn. */
    val sideToMove: Color
        get() = if (moveCount % 2 == 0) Color.WHITE else Color.BLACK

    val isInvitation: Boolean
        get() = inviteeId != null
}

/** The `filter` query parameter of the public game listing. */
enum class GameFilter(val phase: GamePhase) {
    OPEN(GamePhase.WAITING),
    ACTIVE(GamePhase.ACTIVE),
    FINISHED(GamePhase.FINISHED),
}

/** A row of `ratings`: one per user and variant, created by the first rated game. */
data class Rating(
    val userId: Long,
    val kind: GameKind,
    val rating: Int,
    val games: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val peak: Int,
    val updatedAt: Instant,
) {
    companion object {
        /** The rating of everybody who has not played a rated game in the variant yet. */
        const val INITIAL = 1500

        fun initial(userId: Long, kind: GameKind, at: Instant) =
            Rating(userId, kind, INITIAL, games = 0, wins = 0, draws = 0, losses = 0, peak = INITIAL, updatedAt = at)
    }
}

/** A row of `rating_changes`: what a rated game did to one player's rating. */
data class RatingChange(
    val gameId: String,
    val userId: Long,
    val kind: GameKind,
    val before: Int,
    val after: Int,
    val createdAt: Instant,
)

/** The rating rows of both seats of a game. */
data class SeatRatings(val white: Rating, val black: Rating)

/**
 * Maps the rating rows before a game to the rows after it. The repositories apply it inside
 * the transaction that finishes the game, so the rows it reads are the ones it replaces.
 */
typealias RatingUpdate = (before: SeatRatings) -> SeatRatings

/** A leaderboard row: a player and the rating row of the requested variant. */
data class RatedPlayer(val user: User, val rating: Rating)

/** A row of `settings`: a runtime setting that overrides its default (docs/ARCHITECTURE.md section 2.11). */
data class Setting(val key: String, val value: String, val updatedAt: Instant)

/** How many accounts of each kind exist: registered people, guests and bots. */
data class AccountCounts(val users: Int, val guests: Int, val bots: Int)
