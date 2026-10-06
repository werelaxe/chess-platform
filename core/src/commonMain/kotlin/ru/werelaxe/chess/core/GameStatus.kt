package ru.werelaxe.chess.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class EndReason {
    CHECKMATE,
    STALEMATE,
    INSUFFICIENT_MATERIAL,
    FIFTY_MOVE_RULE,
    THREEFOLD_REPETITION,
    KING_CAPTURED,
    RESIGNATION,
    DRAW_AGREEMENT,
    ABANDONMENT,
}

@Serializable
sealed interface GameStatus {
    val isOver: Boolean
        get() = this is Finished

    @Serializable
    @SerialName("ongoing")
    data object Ongoing : GameStatus

    /** [winner] is null for a draw. */
    @Serializable
    @SerialName("finished")
    data class Finished(val winner: Color?, val reason: EndReason) : GameStatus
}
