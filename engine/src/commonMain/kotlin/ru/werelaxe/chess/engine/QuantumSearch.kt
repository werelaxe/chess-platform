package ru.werelaxe.chess.engine

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
import kotlin.time.Duration
import kotlin.time.TimeMark

/**
 * Expectimax over the universe distribution with iterative deepening. Every candidate (normal
 * move, split or observation) is first scored statically on the resulting distribution and
 * then refined with the opponent's best reply (depth 2): the most promising [candidateLimit]
 * always (the first one even past the deadline, so that there is a refined move to play), the
 * rest, in static order, while the first half of the [budget] lasts, so that a quiet position
 * is searched in full and a move that looks poor at a glance but is the only one to save a
 * piece is not lost to the static cut. In a bare endgame (one side ahead by at least a rook
 * against little more than a king, in at least half of the universes) the best
 * [candidateLimit] by depth-2 score are then searched deeper, while the deadline allows, up to
 * [maxDepth] plies, two plies at a time so that the opponent always has the last word, where
 * every inner node orders its replies statically and follows the best few only (a beam). An
 * iteration counts only when it completes for every candidate; the move comes from the
 * deepest one that did. The deeper search is what converts such endgames instead of shuffling
 * for a hundred plies; elsewhere the full-width depth 2 proved the stronger use of the budget
 * (a beam-pruned depth 4 lost matches to it), so it is not attempted. Observations
 * are chance nodes: their value is the probability-weighted average over the possible
 * outcomes. A distribution that already occurred in the game, or earlier in the line, scores
 * as a draw. The refinements of one iteration are spread over [parallelism].
 */
internal class QuantumSearch(
    private val candidateLimit: Int,
    private val maxDepth: Int,
    private val deadline: TimeMark,
    budget: Duration,
    private val randomness: Int,
    private val random: Random,
    private val parallelism: SearchParallelism = SearchParallelism.Sequential,
) {
    private class Candidate(val move: GameMove, val outcomes: List<Pair<Double, QuantumState>>, var score: Int)

    /** Candidates beyond the limit are refined at depth 2 only until this point. */
    private val widenUntil: TimeMark = deadline - budget / 2

    /** The depth of the last completed iteration of the last [bestMove]; for tests and diagnostics. */
    var depthReached: Int = 0
        private set

    fun bestMove(state: QuantumState): GameMove? {
        val me = state.sideToMove
        val candidates = generate(state, me, includeSplits = true)
        if (candidates.isEmpty()) return null
        for (candidate in candidates) {
            candidate.score = expected(candidate.outcomes) { value(it, me, 0, 1, timed = false) }
        }
        candidates.sortByDescending { it.score }

        // Depth 2: the opponent's strongest reply. The first candidate is always refined, so that
        // there is one to choose from; the others within the limit while the deadline has not
        // passed, and the rest while the first half of the budget lasts.
        val scores = parallelism.map(candidates.indices.toList()) { index ->
            val due = when {
                index == 0 -> true
                index < candidateLimit -> !deadline.hasPassedNow()
                else -> !widenUntil.hasPassedNow()
            }
            if (due) expected(candidates[index].outcomes) { value(it, me, 1, 1, timed = false) } else null
        }
        val scored = ArrayList<Candidate>()
        for ((index, score) in scores.withIndex()) {
            if (score == null) continue
            val candidate = candidates[index]
            candidate.score = score
            scored.add(candidate)
        }
        scored.sortByDescending { it.score }
        var refined = ArrayList(scored.take(candidateLimit))
        depthReached = 2

        // Deeper iterations in bare endgames, two plies at a time so that the opponent always
        // has the last word, best candidate first; one cut short by the deadline is discarded whole.
        var depth = if (state.probability(Evaluation::isBareEndgame) >= BARE_ENDGAME_SHARE) 4 else maxDepth + 1
        while (depth <= maxDepth) {
            if (deadline.hasPassedNow()) break
            val deeper = try {
                parallelism.map(refined) { candidate -> expected(candidate.outcomes) { value(it, me, depth - 1, 1, timed = true) } }
            } catch (e: SearchTimeout) {
                break
            }
            for ((index, score) in deeper.withIndex()) refined[index].score = score
            refined = ArrayList(refined.sortedByDescending { it.score })
            depthReached = depth
            depth += 2
        }

        val top = refined.first().score
        val pool = if (randomness > 0) refined.filter { top - it.score <= randomness } else refined.filter { it.score == top }
        return pool[random.nextInt(pool.size)].move
    }

    private inline fun expected(outcomes: List<Pair<Double, QuantumState>>, value: (QuantumState) -> Int): Int {
        var sum = 0.0
        for ((probability, next) in outcomes) sum += probability * value(next)
        return sum.toInt()
    }

    /**
     * The value of [state] for [me] with [depth] more plies of lookahead; [ply] is the distance
     * from the root, so that a quicker win scores higher. With [timed] the deadline is checked
     * at every inner node and [SearchTimeout] thrown when it has passed.
     */
    private fun value(state: QuantumState, me: Color, depth: Int, ply: Int, timed: Boolean): Int {
        terminalValue(state, me, ply)?.let { return it }
        if (state.repetitions >= 2) return Evaluation.DRAW
        if (depth == 0) return evaluate(state, me)
        if (timed && deadline.hasPassedNow()) throw SearchTimeout()
        val mover = state.sideToMove
        val replies = generate(state, mover, includeSplits = false)
        if (replies.isEmpty()) return evaluate(state, me)
        val mine = mover == me
        if (depth == 1) {
            var best: Int? = null
            for (reply in replies) {
                val score = expected(reply.outcomes) { value(it, me, 0, ply + 1, timed) }
                if (best == null || (if (mine) score > best else score < best)) best = score
            }
            return best!!
        }
        for (reply in replies) reply.score = expected(reply.outcomes) { value(it, me, 0, ply + 1, timed) }
        if (mine) replies.sortByDescending { it.score } else replies.sortBy { it.score }
        var best: Int? = null
        for (reply in replies.take(BEAM_WIDTH)) {
            val score = expected(reply.outcomes) { value(it, me, depth - 1, ply + 1, timed) }
            if (best == null || (if (mine) score > best else score < best)) best = score
        }
        return best!!
    }

    private fun terminalValue(state: QuantumState, me: Color, ply: Int): Int? = when (val status = QuantumVariant.status(state)) {
        is GameStatus.Finished -> when (status.winner) {
            me -> Evaluation.MATE - ply
            null -> Evaluation.DRAW
            else -> -Evaluation.MATE + ply
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

        /** Replies followed deeper at an inner node of the deeper iterations. */
        const val BEAM_WIDTH = 8

        /** The probability mass of bare-endgame universes from which the deeper iterations run. */
        const val BARE_ENDGAME_SHARE = 0.5
    }
}
