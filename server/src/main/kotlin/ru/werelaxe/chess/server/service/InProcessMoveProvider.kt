package ru.werelaxe.chess.server.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.QuantumState
import ru.werelaxe.chess.engine.ChessEngine
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.engine.ThinkPriority
import ru.werelaxe.chess.engine.UniverseCeiling

/**
 * Searches with the engine in this process. The search is CPU-bound, so at most [parallelism]
 * run at a time on a dispatcher of that many threads; the others wait their turn, in order of
 * arrival whatever their priority. Replaying the move list first costs about 1% of a search
 * even for a long quantum game.
 *
 * A quantum position with more universes than [maxUniverses] is never searched as it stands,
 * for the same reason as in the engine service: the search would allocate hundreds of states
 * of that many boards each and exhaust this process's heap (which, running with
 * `-XX:+ExitOnOutOfMemoryError`, would take every game and socket down with it). The engine
 * sees its most probable universes only, at [UniverseCeiling.SHALLOW_LEVEL] within
 * [UniverseCeiling.SHALLOW_BUDGET] (see [UniverseCeiling]), logged at WARN once per search.
 */
class InProcessMoveProvider(
    private val engines: (EngineLevel) -> ChessEngine,
    parallelism: Int = DEFAULT_PARALLELISM,
    maxUniverses: Int = UniverseCeiling.DEFAULT_MAX_UNIVERSES,
) : MoveProvider {
    private val log = LoggerFactory.getLogger(InProcessMoveProvider::class.java)
    private val dispatcher = Dispatchers.Default.limitedParallelism(parallelism)
    private val ceiling = UniverseCeiling(maxUniverses)

    override suspend fun chooseMove(
        gameId: String,
        kind: GameKind,
        level: EngineLevel,
        moves: List<GameMove>,
        priority: ThinkPriority,
    ): GameMove? = withContext(dispatcher) {
        val game = Game.replay(kind, moves)
        if (game.isOver) return@withContext null
        val state = game.state
        val restricted = ceiling.restrict(state)
        if (restricted == null) {
            engines(level).chooseMove(state)
        } else {
            log.warn(
                "Position of game {} has {} universes, above the ceiling {}; answering with a shallow search on its {} most probable",
                gameId, (state as QuantumState).universeCount, ceiling.maxUniverses, restricted.universeCount,
            )
            engines(UniverseCeiling.SHALLOW_LEVEL).chooseMove(restricted, UniverseCeiling.SHALLOW_BUDGET)
        }
    }

    companion object {
        const val DEFAULT_PARALLELISM = 2
    }
}
