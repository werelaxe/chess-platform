package ru.werelaxe.chess.server.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.GameStatus
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.model.GameResult
import ru.werelaxe.chess.server.model.Rating
import ru.werelaxe.chess.server.model.User
import ru.werelaxe.chess.server.model.Visibility

/**
 * A player as others see them. [guest] and [bot] are omitted from the JSON when false (defaults
 * are not encoded); [name] is the display name, null until set; [botLevel] is set for bots
 * only; [rating] is the rating in the kind of the game the reference appears in, null for
 * guests and outside games (player listings carry the ratings separately).
 */
@Serializable
data class UserRef(
    val id: Long,
    val username: String,
    val name: String?,
    val guest: Boolean = false,
    val bot: Boolean = false,
    val botLevel: EngineLevel?,
    val rating: Int?,
) {
    companion object {
        fun of(user: User, rating: Int? = null) =
            UserRef(user.id, user.username, user.displayName, user.isGuest, user.isBot, user.botLevel, rating)
    }
}

/** One variant's rating row of a player. */
@Serializable
data class RatingSummary(
    val rating: Int,
    val games: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val peak: Int,
) {
    companion object {
        fun of(rating: Rating) =
            RatingSummary(rating.rating, rating.games, rating.wins, rating.draws, rating.losses, rating.peak)
    }
}

/** A player's ratings per variant; a variant is null until the player finishes a rated game in it. */
@Serializable
data class PlayerRatings(
    @SerialName("CLASSIC") val classic: RatingSummary?,
    @SerialName("QUANTUM") val quantum: RatingSummary?,
) {
    companion object {
        val NONE = PlayerRatings(null, null)

        fun of(rows: Collection<Rating>) = PlayerRatings(
            classic = rows.firstOrNull { it.kind == GameKind.CLASSIC }?.let(RatingSummary::of),
            quantum = rows.firstOrNull { it.kind == GameKind.QUANTUM }?.let(RatingSummary::of),
        )
    }
}

/**
 * The caller's own account, returned by the auth endpoints only: the [UserRef] fields (without
 * the game-bound rating) plus the settings that only the owner sees and the ratings. [locale]
 * is always encoded, as null until the user picks a language.
 */
@Serializable
data class UserProfile(
    val id: Long,
    val username: String,
    val name: String?,
    val guest: Boolean = false,
    val bot: Boolean = false,
    val botLevel: EngineLevel?,
    val locale: String?,
    val ratings: PlayerRatings,
) {
    companion object {
        fun of(user: User, ratings: PlayerRatings) =
            UserProfile(user.id, user.username, user.displayName, user.isGuest, user.isBot, user.botLevel, user.locale, ratings)
    }
}

@Serializable
data class CredentialsRequest(val username: String, val password: String)

@Serializable
data class AuthResponse(val token: String, val user: UserProfile)

@Serializable
enum class ColorChoice {
    WHITE,
    BLACK,
    RANDOM,
}

/** [OPEN]: anybody may join. [USER]: an invitation that only `opponentUsername` may join. */
@Serializable
enum class Opponent {
    OPEN,
    USER,
}

/** [rated] is forced to false for guests; [opponentUsername] is required with [Opponent.USER] and ignored otherwise. */
@Serializable
data class CreateGameRequest(
    val kind: GameKind,
    val visibility: Visibility = Visibility.PUBLIC,
    val color: ColorChoice = ColorChoice.RANDOM,
    val rated: Boolean = true,
    val opponent: Opponent = Opponent.OPEN,
    val opponentUsername: String? = null,
)

@Serializable
data class GameSummary(
    val id: String,
    val kind: GameKind,
    val visibility: Visibility,
    val status: GamePhase,
    val rated: Boolean,
    val white: UserRef?,
    val black: UserRef?,
    val creator: UserRef,
    /** The invited player of an invitation (set while WAITING and kept afterwards); null for an open game. */
    val invitee: UserRef?,
    val moveCount: Int,
    val result: GameResult?,
    val drawOfferedBy: Color?,
    /**
     * True only in the DTO that reports an invitation the invitee declined (omitted from the JSON
     * otherwise), so that the creator can tell a decline from their own cancellation, which is
     * reported the same way (FINISHED by ABANDONMENT).
     */
    val declined: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
) {
    fun withMoves(moves: List<GameMove>, ratingChanges: GameRatingChanges?) = GameDto(
        id = id,
        kind = kind,
        visibility = visibility,
        status = status,
        rated = rated,
        white = white,
        black = black,
        creator = creator,
        invitee = invitee,
        moveCount = moveCount,
        result = result,
        drawOfferedBy = drawOfferedBy,
        declined = declined,
        ratingChanges = ratingChanges,
        createdAt = createdAt,
        updatedAt = updatedAt,
        moves = moves,
    )
}

/** What a rated game did to one player's rating. */
@Serializable
data class RatingDelta(val before: Int, val after: Int)

/** The rating changes of a finished rated game, per seat. */
@Serializable
data class GameRatingChanges(val white: RatingDelta, val black: RatingDelta)

/**
 * [GameSummary] plus the move list, which is the only persistent form of the position, and the
 * rating changes of a finished rated game (null otherwise).
 */
@Serializable
data class GameDto(
    val id: String,
    val kind: GameKind,
    val visibility: Visibility,
    val status: GamePhase,
    val rated: Boolean,
    val white: UserRef?,
    val black: UserRef?,
    val creator: UserRef,
    val invitee: UserRef?,
    val moveCount: Int,
    val result: GameResult?,
    val drawOfferedBy: Color?,
    val declined: Boolean = false,
    val ratingChanges: GameRatingChanges?,
    val createdAt: String,
    val updatedAt: String,
    val moves: List<GameMove>,
)

@Serializable
data class GamesResponse(val games: List<GameSummary>)

/** The caller's pending invitations: received and sent. */
@Serializable
data class InvitationsResponse(val incoming: List<GameSummary>, val outgoing: List<GameSummary>)

@Serializable
data class PlayerSummary(val user: UserRef, val ratings: PlayerRatings)

/** A [PlayerSummary] with its position and the rating of the requested variant. */
@Serializable
data class LeaderboardEntry(val user: UserRef, val ratings: PlayerRatings, val rank: Int, val rating: Int)

/** A [PlayerSummary] plus what the profile page shows; [lastSeen] is null for bots and never-seen accounts. */
@Serializable
data class PlayerProfile(
    val user: UserRef,
    val ratings: PlayerRatings,
    val memberSince: String,
    val lastSeen: String?,
    val recentGames: List<GameSummary>,
)

@Serializable
data class PlayersResponse(val players: List<PlayerSummary>)

@Serializable
data class LeaderboardResponse(val entries: List<LeaderboardEntry>)

@Serializable
data class BotsResponse(val bots: List<PlayerSummary>)

@Serializable
data class MoveRequest(val move: GameMove)

@Serializable
data class MoveResponse(val ply: Int, val move: GameMove, val status: GameStatus)

@Serializable
enum class DrawAction {
    OFFER,
    ACCEPT,
    DECLINE,
    WITHDRAW,
}

@Serializable
data class DrawRequest(val action: DrawAction)

@Serializable
data class ErrorResponse(val error: String, val message: String)

@Serializable
data class HealthResponse(val status: String)
