package ru.werelaxe.chess.server.dto

import kotlinx.serialization.Serializable
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.GameStatus
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.model.GameResult
import ru.werelaxe.chess.server.model.User
import ru.werelaxe.chess.server.model.Visibility

/** [guest] and [bot] are omitted from the JSON when false (defaults are not encoded). */
@Serializable
data class UserRef(val id: Long, val username: String, val guest: Boolean = false, val bot: Boolean = false) {
    companion object {
        fun of(user: User) = UserRef(user.id, user.username, user.isGuest, user.isBot)
    }
}

@Serializable
data class CredentialsRequest(val username: String, val password: String)

@Serializable
data class AuthResponse(val token: String, val user: UserRef)

@Serializable
enum class ColorChoice {
    WHITE,
    BLACK,
    RANDOM,
}

@Serializable
enum class Opponent {
    HUMAN,
    COMPUTER,
}

/** [level] is required for a [Opponent.COMPUTER] opponent and ignored otherwise. */
@Serializable
data class CreateGameRequest(
    val kind: GameKind,
    val visibility: Visibility = Visibility.PUBLIC,
    val color: ColorChoice = ColorChoice.RANDOM,
    val opponent: Opponent = Opponent.HUMAN,
    val level: EngineLevel? = null,
)

@Serializable
data class GameSummary(
    val id: String,
    val kind: GameKind,
    val visibility: Visibility,
    val status: GamePhase,
    val white: UserRef?,
    val black: UserRef?,
    val creator: UserRef,
    val moveCount: Int,
    val result: GameResult?,
    val drawOfferedBy: Color?,
    /** The computer's level in a game against it; null between people. */
    val botLevel: EngineLevel?,
    val createdAt: String,
    val updatedAt: String,
) {
    fun withMoves(moves: List<GameMove>) = GameDto(
        id = id,
        kind = kind,
        visibility = visibility,
        status = status,
        white = white,
        black = black,
        creator = creator,
        moveCount = moveCount,
        result = result,
        drawOfferedBy = drawOfferedBy,
        botLevel = botLevel,
        createdAt = createdAt,
        updatedAt = updatedAt,
        moves = moves,
    )
}

/** [GameSummary] plus the move list, which is the only persistent form of the position. */
@Serializable
data class GameDto(
    val id: String,
    val kind: GameKind,
    val visibility: Visibility,
    val status: GamePhase,
    val white: UserRef?,
    val black: UserRef?,
    val creator: UserRef,
    val moveCount: Int,
    val result: GameResult?,
    val drawOfferedBy: Color?,
    val botLevel: EngineLevel?,
    val createdAt: String,
    val updatedAt: String,
    val moves: List<GameMove>,
)

@Serializable
data class GamesResponse(val games: List<GameSummary>)

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
