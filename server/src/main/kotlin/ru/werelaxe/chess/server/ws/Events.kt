package ru.werelaxe.chess.server.ws

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.GameStatus
import ru.werelaxe.chess.server.dto.GameDto

/** Server-to-client WebSocket events, discriminated by `type`. */
@Serializable
sealed interface ServerEvent {
    @Serializable
    @SerialName("move")
    data class Move(val ply: Int, val move: GameMove, val status: GameStatus) : ServerEvent

    /** Sent on connect and whenever players, status or the draw offer change. */
    @Serializable
    @SerialName("game")
    data class GameUpdated(val game: GameDto) : ServerEvent

    @Serializable
    @SerialName("pong")
    data object Pong : ServerEvent
}

/** Client-to-server WebSocket messages; only `ping` exists so far. */
@Serializable
data class ClientMessage(val type: String)

/** Where the game service publishes events; implemented by [GameHub]. */
interface GameEvents {
    fun publish(gameId: String, event: ServerEvent)
}
