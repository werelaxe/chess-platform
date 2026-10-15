package ru.werelaxe.chess.engine

import ru.werelaxe.chess.core.ClassicState
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.GameState
import ru.werelaxe.chess.core.QuantumState
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * The built-in computer player for both variants. Pure and stateless: it reads an immutable
 * state and returns a move, so the same code runs on the server and in future offline clients.
 * Observations are returned without an outcome; the authoritative side resolves them.
 *
 * [budget] replaces the level's wall-clock budget for one move (a loaded engine service
 * shrinks it); the depth, candidate count and randomness of the level are unaffected.
 * [parallelism] lets a search use more than one thread without changing what it finds.
 */
class ChessEngine(
    private val level: EngineLevel,
    private val random: Random = Random.Default,
    private val parallelism: SearchParallelism = SearchParallelism.Sequential,
) {
    fun chooseMove(game: Game, budget: Duration = level.timeBudget): GameMove? =
        if (game.isOver) null else chooseMove(game.state, budget)

    fun chooseMove(state: GameState, budget: Duration = level.timeBudget): GameMove? {
        val deadline = TimeSource.Monotonic.markNow() + budget
        return when (state) {
            is ClassicState -> ClassicSearch(level.classicDepth, level.quiescence, deadline, level.randomness, random, parallelism, state.positionCounts.keys)
                .bestMove(state.board)
                ?.let { GameMove.Normal(it.move.from, it.move.to, it.move.promotion) }
            is QuantumState -> QuantumSearch(level.quantumCandidates, level.quantumDepth, deadline, budget, level.randomness, random, parallelism, level.splitCost).bestMove(state)
        }
    }
}
