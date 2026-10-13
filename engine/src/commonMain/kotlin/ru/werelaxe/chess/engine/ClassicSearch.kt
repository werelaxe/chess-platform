package ru.werelaxe.chess.engine

import ru.werelaxe.chess.core.Board
import ru.werelaxe.chess.core.ClassicRules
import ru.werelaxe.chess.core.Move
import kotlin.random.Random
import kotlin.time.TimeMark

internal class SearchTimeout : RuntimeException()

/**
 * Negamax with alpha-beta pruning, iterative deepening, MVV-LVA move ordering and an optional
 * quiescence search on captures. Stops at [deadline]; the best move of the last completed
 * iteration is returned. The root moves of one iteration are spread over [parallelism].
 */
internal class ClassicSearch(
    private val maxDepth: Int,
    private val quiescence: Boolean,
    private val deadline: TimeMark,
    private val randomness: Int,
    private val random: Random,
    private val parallelism: SearchParallelism = SearchParallelism.Sequential,
) {
    class Result(val move: Move, val score: Int, val depth: Int)

    fun bestMove(board: Board): Result? {
        var moves = order(board, ClassicRules.legalMoves(board))
        if (moves.isEmpty()) return null
        var best: Result? = null
        for (depth in 1..maxDepth) {
            val root = try {
                searchRoot(board, moves, depth)
            } catch (e: SearchTimeout) {
                break
            }
            val scores = root.scores
            // An upper bound can equal the best score; the exact one is the best move then.
            val ranked = moves.indices.sortedWith(compareByDescending<Int> { scores[it] }.thenByDescending { root.exact[it] })
            val topScore = scores[ranked.first()]
            val chosen = if (randomness > 0) {
                val pool = ranked.filter { topScore - scores[it] <= randomness }
                pool[random.nextInt(pool.size)]
            } else {
                ranked.first()
            }
            best = Result(moves[chosen], scores[chosen], depth)
            // Search the previous iteration's best moves first next time.
            moves = ranked.map { moves[it] }
            if (topScore >= Evaluation.MATE - 100) break
        }
        return best
    }

    /** The scores of the root moves and which of them are exact rather than upper bounds. */
    private class RootScores(val scores: IntArray, val exact: BooleanArray)

    private class RootScore(val score: Int, val exact: Boolean)

    /**
     * Scores every root move at [depth]. The first one (the best of the previous iteration) is
     * searched alone so that a lower bound is known; the others are searched through
     * [parallelism] against the bound as it stands when their search starts and raise it when
     * they beat it ("young brothers wait"). As in any alpha-beta search, a move that does not
     * beat the bound gets an upper bound on its score rather than an exact value; the bound
     * only ever grows through exact scores, so the top score is exact whichever order the
     * moves were searched in.
     */
    private fun searchRoot(board: Board, moves: List<Move>, depth: Int): RootScores {
        val alpha = parallelism.sharedBound(-Evaluation.MATE - 1)
        val first = Line().score(board, moves[0], depth, alpha.get())
        alpha.raise(first)
        val rest = parallelism.map(moves.subList(1, moves.size)) { move ->
            val bound = alpha.get()
            val score = Line().score(board, move, depth, bound)
            alpha.raise(score)
            RootScore(score, exact = score > bound)
        }
        val scores = IntArray(moves.size)
        val exact = BooleanArray(moves.size)
        scores[0] = first
        exact[0] = true
        for (index in rest.indices) {
            scores[index + 1] = rest[index].score
            exact[index + 1] = rest[index].exact
        }
        return RootScores(scores, exact)
    }

    /** Captures of valuable pieces by cheap ones first, then promotions, then quiet moves. */
    private fun order(board: Board, moves: List<Move>): List<Move> =
        moves.sortedByDescending { move ->
            val victim = board[move.to]?.let { Evaluation.value(it.type) } ?: 0
            val attacker = board[move.from]?.let { Evaluation.value(it.type) } ?: 0
            val promotion = move.promotion?.let { Evaluation.value(it) } ?: 0
            if (victim > 0) 10_000 + victim * 10 - attacker / 10 + promotion else promotion
        }

    /**
     * The search below one root move, with its own node counter for the time check so that
     * root moves searched concurrently share nothing but the bound.
     */
    private inner class Line {
        private var nodes = 0L

        /** The score of [move] for the side to move: exact when above [alpha], an upper bound otherwise. */
        fun score(board: Board, move: Move, depth: Int, alpha: Int): Int =
            -negamax(ClassicRules.apply(board, move), depth - 1, -Evaluation.MATE - 1, -alpha, 1)

        private fun negamax(board: Board, depth: Int, alphaIn: Int, beta: Int, ply: Int): Int {
            checkTime()
            val moves = ClassicRules.legalMoves(board)
            if (moves.isEmpty()) {
                return if (ClassicRules.isInCheck(board, board.sideToMove)) -Evaluation.MATE + ply else 0
            }
            if (ClassicRules.isInsufficientMaterial(board)) return 0
            if (depth <= 0) return if (quiescence) quiesce(board, alphaIn, beta, ply) else Evaluation.evaluate(board, board.sideToMove)

            var alpha = alphaIn
            var best = -Evaluation.MATE - 1
            for (move in order(board, moves)) {
                val score = -negamax(ClassicRules.apply(board, move), depth - 1, -beta, -alpha, ply + 1)
                if (score > best) best = score
                if (score > alpha) alpha = score
                if (alpha >= beta) break
            }
            return best
        }

        private fun quiesce(board: Board, alphaIn: Int, beta: Int, ply: Int): Int {
            checkTime()
            val standPat = Evaluation.evaluate(board, board.sideToMove)
            if (standPat >= beta) return beta
            var alpha = maxOf(alphaIn, standPat)
            if (ply > MAX_QUIESCENCE_PLY) return alpha
            val captures = ClassicRules.legalMoves(board).filter { ClassicRules.isCapture(board, it) || it.promotion != null }
            for (move in order(board, captures)) {
                val score = -quiesce(ClassicRules.apply(board, move), -beta, -alpha, ply + 1)
                if (score >= beta) return beta
                if (score > alpha) alpha = score
            }
            return alpha
        }

        private fun checkTime() {
            nodes++
            if (nodes and 1023L == 0L && deadline.hasPassedNow()) throw SearchTimeout()
        }
    }

    private companion object {
        const val MAX_QUIESCENCE_PLY = 12
    }
}
