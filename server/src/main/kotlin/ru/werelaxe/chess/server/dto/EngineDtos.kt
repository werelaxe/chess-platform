package ru.werelaxe.chess.server.dto

import kotlinx.serialization.Serializable
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.engine.ThinkPriority

/**
 * Body of the engine service's `POST /think` (docs/ARCHITECTURE.md section 2.7): the whole game
 * so far and who waits for the move ([priority]; the service serves humans first).
 */
@Serializable
data class ThinkRequest(
    val gameId: String,
    val kind: GameKind,
    val level: EngineLevel,
    val moves: List<GameMove>,
    val priority: ThinkPriority,
)

/** The engine service's answer; [move] carries no outcome when it is an observation. */
@Serializable
data class ThinkResponse(val move: GameMove, val elapsedMillis: Long = 0)
