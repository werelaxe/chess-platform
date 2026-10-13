package ru.werelaxe.chess.engineservice.service

import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.GameState
import ru.werelaxe.chess.engine.ChessEngine
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.engine.SearchParallelism
import kotlin.time.Duration

/** What the [Searcher] needs from an engine; tests plug in stubs, production uses [ChessEngines]. */
fun interface Engine {
    /** Chooses a move for the side on turn in [state], an ongoing position, within [budget]. */
    fun chooseMove(state: GameState, level: EngineLevel, budget: Duration): GameMove?
}

/**
 * The real engine, one instance per level, searching on [parallelism]; [ChessEngine] is
 * stateless, so the instances are shared by all threads.
 */
class ChessEngines(parallelism: SearchParallelism) : Engine {
    private val engines = EngineLevel.entries.associateWith { ChessEngine(it, parallelism = parallelism) }

    override fun chooseMove(state: GameState, level: EngineLevel, budget: Duration): GameMove? =
        engines.getValue(level).chooseMove(state, budget)
}
