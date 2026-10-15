package ru.werelaxe.chess.engineservice.dto

import kotlinx.serialization.Serializable
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.engine.ThinkPriority

/**
 * The position to think about, as the move list of the game; [gameId] only keys the replay
 * cache. [priority] says who waits for the move; a request without it (an older API) is a
 * human's, so that nobody is served last by mistake.
 */
@Serializable
data class ThinkRequest(
    val gameId: String,
    val kind: GameKind,
    val level: EngineLevel,
    val moves: List<GameMove>,
    val priority: ThinkPriority = ThinkPriority.HUMAN,
)

/** The chosen move (an observation carries no outcome) and the time the search took. */
@Serializable
data class ThinkResponse(val move: GameMove, val elapsedMillis: Long)

/**
 * [inFlight] searches are running, [queued] requests are waiting for one of the [parallelism]
 * threads ([queuedBots] of them for bot-versus-bot games, served after the humans), and
 * [helpersBusy] of the [helpers] threads are lent to the running searches.
 */
@Serializable
data class HealthResponse(
    val status: String,
    val parallelism: Int,
    val inFlight: Int,
    val queued: Int,
    val queuedBots: Int,
    val helpers: Int,
    val helpersBusy: Int,
)

@Serializable
data class ErrorResponse(val error: String, val message: String)
