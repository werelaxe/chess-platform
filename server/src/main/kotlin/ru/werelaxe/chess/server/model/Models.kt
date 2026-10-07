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
 * for the computer player ([isBot]), which never logs in at all.
 */
data class User(
    val id: Long,
    val username: String,
    val passwordHash: String?,
    val isGuest: Boolean,
    val isBot: Boolean,
    val createdAt: Instant,
)

/**
 * A row of the `games` table; the moves live separately and are the only game state.
 * [botLevel] is set for games against the computer, in which the creator is the human.
 */
data class GameRecord(
    val id: String,
    val kind: GameKind,
    val visibility: Visibility,
    val phase: GamePhase,
    val creatorId: Long,
    val whiteId: Long?,
    val blackId: Long?,
    val moveCount: Int,
    val result: GameResult?,
    val drawOfferedBy: Color?,
    val botLevel: EngineLevel?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val finishedAt: Instant?,
) {
    fun colorOf(userId: Long): Color? = when {
        whiteId != null && whiteId == userId -> Color.WHITE
        blackId != null && blackId == userId -> Color.BLACK
        else -> null
    }

    fun involves(userId: Long): Boolean = creatorId == userId || colorOf(userId) != null

    /** The computer's color in a computer game: the seat the (human) creator does not hold. */
    val botColor: Color?
        get() = if (botLevel == null) null else colorOf(creatorId)?.opposite

    /**
     * Whether the computer is on turn, judged from the move count alone: every move of either
     * variant passes the turn, so an even count means white to move.
     */
    val isBotsTurn: Boolean
        get() = phase == GamePhase.ACTIVE && botColor == (if (moveCount % 2 == 0) Color.WHITE else Color.BLACK)
}

/** The `filter` query parameter of the public game listing. */
enum class GameFilter(val phase: GamePhase) {
    OPEN(GamePhase.WAITING),
    ACTIVE(GamePhase.ACTIVE),
    FINISHED(GamePhase.FINISHED),
}
