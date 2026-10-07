package ru.werelaxe.chess.core.engine

import ru.werelaxe.chess.core.Board
import ru.werelaxe.chess.core.ClassicRules
import ru.werelaxe.chess.core.Move
import ru.werelaxe.chess.core.PieceType
import kotlin.random.Random
import kotlin.time.TimeMark

internal class SearchTimeout : RuntimeException()

/**
 * Negamax with alpha-beta pruning, iterative deepening, MVV-LVA move ordering and an optional
 * quiescence search on captures. Stops at [deadline]; the best move of the last completed
 * iteration is returned.
 */
internal class ClassicSearch(
    private val maxDepth: Int,
    private val quiescence: Boolean,
    private val deadline: TimeMark,
    private val randomness: Int,
    private val random: Random,
) {
    private var nodes = 0L

    class Result(val move: Move, val score: Int, val depth: Int)

    fun bestMove(board: Board): Result? {
        var moves = order(board, ClassicRules.legalMoves(board))
        if (moves.isEmpty()) return null
        var best: Result? = null
        for (depth in 1..maxDepth) {
            val scores = try {
                searchRoot(board, moves, depth)
            } catch (e: SearchTimeout) {
                break
            }
            val ranked = moves.indices.sortedByDescending { scores[it] }
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

    private fun searchRoot(board: Board, moves: List<Move>, depth: Int): IntArray {
        val scores = IntArray(moves.size)
        var alpha = -Evaluation.MATE - 1
        val beta = Evaluation.MATE + 1
        for ((index, move) in moves.withIndex()) {
            val score = -negamax(ClassicRules.apply(board, move), depth - 1, -beta, -alpha, 1)
            scores[index] = score
            if (score > alpha) alpha = score
        }
        return scores
    }

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

    /** Captures of valuable pieces by cheap ones first, then promotions, then quiet moves. */
    private fun order(board: Board, moves: List<Move>): List<Move> =
        moves.sortedByDescending { move ->
            val victim = board[move.to]?.let { Evaluation.value(it.type) } ?: 0
            val attacker = board[move.from]?.let { Evaluation.value(it.type) } ?: 0
            val promotion = move.promotion?.let { Evaluation.value(it) } ?: 0
            if (victim > 0) 10_000 + victim * 10 - attacker / 10 + promotion else promotion
        }

    private fun checkTime() {
        nodes++
        if (nodes and 1023L == 0L && deadline.hasPassedNow()) throw SearchTimeout()
    }

    private companion object {
        const val MAX_QUIESCENCE_PLY = 12
    }
}
