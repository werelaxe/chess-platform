package ru.werelaxe.chess.server.model

import kotlinx.serialization.Serializable
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.GameKind
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

/** [passwordHash] is null for guests, who cannot log in and exist only through their token. */
data class User(
    val id: Long,
    val username: String,
    val passwordHash: String?,
    val isGuest: Boolean,
    val createdAt: Instant,
)

/** A row of the `games` table; the moves live separately and are the only game state. */
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
}

/** The `filter` query parameter of the public game listing. */
enum class GameFilter(val phase: GamePhase) {
    OPEN(GamePhase.WAITING),
    ACTIVE(GamePhase.ACTIVE),
    FINISHED(GamePhase.FINISHED),
}
