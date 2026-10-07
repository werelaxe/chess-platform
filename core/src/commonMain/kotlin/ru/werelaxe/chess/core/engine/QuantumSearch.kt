package ru.werelaxe.chess.core.engine

import ru.werelaxe.chess.core.Board
import ru.werelaxe.chess.core.ClassicRules
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.GameStatus
import ru.werelaxe.chess.core.Observation
import ru.werelaxe.chess.core.PieceType
import ru.werelaxe.chess.core.QuantumState
import ru.werelaxe.chess.core.QuantumVariant
import ru.werelaxe.chess.core.Square
import kotlin.random.Random
import kotlin.time.TimeMark

/**
 * Expectimax over the universe distribution. Every candidate (normal move, split or
 * observation) is first scored statically on the resulting distribution; the most promising
 * ones are then refined with the opponent's best reply. Observations are chance nodes: their
 * value is the probability-weighted average over the possible outcomes.
 */
internal class QuantumSearch(
    private val candidateLimit: Int,
    private val deadline: TimeMark,
    private val randomness: Int,
    private val random: Random,
) {
    private class Candidate(val move: GameMove, val outcomes: List<Pair<Double, QuantumState>>, var score: Int)

    fun bestMove(state: QuantumState): GameMove? {
        val me = state.sideToMove
        val candidates = generate(state, me, includeSplits = true)
        if (candidates.isEmpty()) return null
        for (candidate in candidates) {
            candidate.score = expected(candidate.outcomes) { evaluateAfterMyMove(it, me) }
        }
        candidates.sortByDescending { it.score }

        // Refine the top candidates with the opponent's strongest reply while time allows.
        val refined = ArrayList<Candidate>()
        for (candidate in candidates.take(candidateLimit)) {
            if (deadline.hasPassedNow() && refined.isNotEmpty()) break
            candidate.score = expected(candidate.outcomes) { opponentReplyValue(it, me) }
            refined.add(candidate)
        }
        refined.sortByDescending { it.score }
        val top = refined.first().score
        val pool = if (randomness > 0) refined.filter { top - it.score <= randomness } else refined.filter { it.score == top }
        return pool[random.nextInt(pool.size)].move
    }

    private inline fun expected(outcomes: List<Pair<Double, QuantumState>>, value: (QuantumState) -> Int): Int {
        var sum = 0.0
        for ((probability, next) in outcomes) sum += probability * value(next)
        return sum.toInt()
    }

    /** Static value of a distribution right after [me] moved (so it is the opponent's turn). */
    private fun evaluateAfterMyMove(state: QuantumState, me: Color): Int {
        terminalValue(state, me)?.let { return it }
        return evaluate(state, me)
    }

    /** Value after the opponent's best normal move or observation; falls back to the static value. */
    private fun opponentReplyValue(state: QuantumState, me: Color): Int {
        terminalValue(state, me)?.let { return it }
        var worst: Int? = null
        for (reply in generate(state, me.opposite, includeSplits = false)) {
            val value = expected(reply.outcomes) { next -> terminalValue(next, me) ?: evaluate(next, me) }
            if (worst == null || value < worst) worst = value
        }
        return worst ?: evaluate(state, me)
    }

    private fun terminalValue(state: QuantumState, me: Color): Int? = when (val status = QuantumVariant.status(state)) {
        is GameStatus.Finished -> when (status.winner) {
            me -> Evaluation.MATE
            null -> 0
            else -> -Evaluation.MATE
        }
        GameStatus.Ongoing -> null
    }

    /**
     * Probability-weighted classical evaluation plus king terms: a universe where one side has
     * no king counts heavily, and a king that can be captured on the opponent's turn is a risk.
     */
    internal fun evaluate(state: QuantumState, me: Color): Int {
        val opponent = me.opposite
        var sum = 0.0
        val total = state.totalWeight.toDouble()
        for ((board, weight) in state.universes) {
            var value = Evaluation.evaluate(board, me)
            val myKing = board.hasKing(me)
            val theirKing = board.hasKing(opponent)
            if (!myKing) value -= Evaluation.KING_PRESENCE
            if (!theirKing) value += Evaluation.KING_PRESENCE
            if (myKing && theirKing) {
                if (board.sideToMove == opponent && ClassicRules.isInCheck(board, me)) value -= CHECK_RISK
                if (board.sideToMove == me && ClassicRules.isInCheck(board, opponent)) value += CHECK_RISK
            }
            sum += value * weight
        }
        return (sum / total).toInt()
    }

    private fun generate(state: QuantumState, mover: Color, includeSplits: Boolean): MutableList<Candidate> {
        val result = ArrayList<Candidate>()
        val pieceSquares = Square.ALL.filter { square -> state.universes.keys.any { it[square]?.color == mover } }
        val normalByOrigin = HashMap<Square, MutableList<Pair<Square, Candidate>>>()

        for (from in pieceSquares) {
            val targets = QuantumVariant.legalTargets(state, from)
            for (to in targets) {
                val promotion = if (QuantumVariant.requiresPromotion(state, from, to)) PieceType.QUEEN else null
                val move = GameMove.Normal(from, to, promotion)
                val next = QuantumVariant.apply(state, move)
                val candidate = Candidate(move, listOf(1.0 to next), 0)
                result.add(candidate)
                normalByOrigin.getOrPut(from) { ArrayList() }.add(to to candidate)
            }
        }

        for (square in Square.ALL) {
            if (!QuantumVariant.canObserve(state, square)) continue
            val outcomes = state.distribution(square).map { (piece, probability) ->
                probability to QuantumVariant.apply(state, GameMove.Observe(square, Observation(piece)))
            }
            result.add(Candidate(GameMove.Observe(square), outcomes, 0))
        }

        if (!includeSplits) return result

        // Splits: pairs of the best few targets of each piece, plus "stay".
        for ((from, entries) in normalByOrigin) {
            val ranked = entries
                .map { (to, candidate) -> to to evaluate(candidate.outcomes[0].second, mover) }
                .sortedByDescending { it.second }
                .take(SPLIT_TARGETS_PER_PIECE)
                .map { it.first }
            for (i in ranked.indices) {
                val first = ranked[i]
                val promotion = if (QuantumVariant.requiresPromotion(state, from, first)) PieceType.QUEEN else null
                val seconds = ranked.drop(i + 1) + from
                for (second in seconds) {
                    val move = GameMove.Split(from, first, second, promotion)
                    if (!QuantumVariant.isLegal(state, move)) continue
                    result.add(Candidate(move, listOf(1.0 to QuantumVariant.apply(state, move)), 0))
                }
            }
        }
        return result
    }

    private companion object {
        const val SPLIT_TARGETS_PER_PIECE = 3
        const val CHECK_RISK = 900
    }
}
