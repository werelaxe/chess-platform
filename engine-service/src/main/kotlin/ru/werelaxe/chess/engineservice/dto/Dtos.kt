package ru.werelaxe.chess.engineservice.dto

import kotlinx.serialization.Serializable
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.engine.EngineLevel

/** The position to think about, as the move list of the game; [gameId] only keys the replay cache. */
@Serializable
data class ThinkRequest(
    val gameId: String,
    val kind: GameKind,
    val level: EngineLevel,
    val moves: List<GameMove>,
)

/** The chosen move (an observation carries no outcome) and the time the search took. */
@Serializable
data class ThinkResponse(val move: GameMove, val elapsedMillis: Long)

/** [inFlight] searches are running, [queued] requests are waiting for one of the [parallelism] threads. */
@Serializable
data class HealthResponse(val status: String, val parallelism: Int, val inFlight: Int, val queued: Int)

@Serializable
data class ErrorResponse(val error: String, val message: String)
