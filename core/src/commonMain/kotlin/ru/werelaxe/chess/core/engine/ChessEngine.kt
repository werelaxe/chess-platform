package ru.werelaxe.chess.core.engine

import ru.werelaxe.chess.core.ClassicState
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.GameState
import ru.werelaxe.chess.core.QuantumState
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * The built-in computer player for both variants. Pure and stateless: it reads an immutable
 * state and returns a move, so the same code runs on the server and in future offline clients.
 * Observations are returned without an outcome; the authoritative side resolves them.
 */
class ChessEngine(
    private val level: EngineLevel,
    private val random: Random = Random.Default,
) {
    fun chooseMove(game: Game): GameMove? = if (game.isOver) null else chooseMove(game.state)

    fun chooseMove(state: GameState): GameMove? {
        val deadline = TimeSource.Monotonic.markNow() + level.timeBudget
        return when (state) {
            is ClassicState -> ClassicSearch(level.classicDepth, level.quiescence, deadline, level.randomness, random)
                .bestMove(state.board)
                ?.let { GameMove.Normal(it.move.from, it.move.to, it.move.promotion) }
            is QuantumState -> QuantumSearch(level.quantumCandidates, deadline, level.randomness, random).bestMove(state)
        }
    }
}
