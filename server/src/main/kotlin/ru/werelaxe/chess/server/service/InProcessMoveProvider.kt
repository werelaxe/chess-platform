package ru.werelaxe.chess.server.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.engine.ChessEngine
import ru.werelaxe.chess.engine.EngineLevel

/**
 * Searches with the engine in this process. The search is CPU-bound, so at most [parallelism]
 * run at a time on a dispatcher of that many threads; the others wait their turn. Replaying the
 * move list first costs about 1% of a search even for a long quantum game.
 */
class InProcessMoveProvider(
    private val engines: (EngineLevel) -> ChessEngine,
    parallelism: Int = DEFAULT_PARALLELISM,
) : MoveProvider {
    private val dispatcher = Dispatchers.Default.limitedParallelism(parallelism)

    override suspend fun chooseMove(gameId: String, kind: GameKind, level: EngineLevel, moves: List<GameMove>): GameMove? =
        withContext(dispatcher) {
            engines(level).chooseMove(Game.replay(kind, moves))
        }

    companion object {
        const val DEFAULT_PARALLELISM = 2
    }
}
