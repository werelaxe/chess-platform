package ru.werelaxe.chess.engineservice.service

import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.GameState
import ru.werelaxe.chess.engine.ChessEngine
import ru.werelaxe.chess.engine.EngineLevel
import kotlin.time.Duration

/** What the [Searcher] needs from an engine; tests plug in stubs, production uses [ChessEngines]. */
fun interface Engine {
    /** Chooses a move for the side on turn in [state], an ongoing position, within [budget]. */
    fun chooseMove(state: GameState, level: EngineLevel, budget: Duration): GameMove?
}

/** The real engine, one instance per level; [ChessEngine] is stateless, so they are shared by all threads. */
object ChessEngines : Engine {
    private val engines = EngineLevel.entries.associateWith { ChessEngine(it) }

    override fun chooseMove(state: GameState, level: EngineLevel, budget: Duration): GameMove? =
        engines.getValue(level).chooseMove(state, budget)
}
