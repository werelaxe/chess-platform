package ru.werelaxe.chess.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A knight that keeps splitting between two squares with "stay" produces coprime weights
 * (consecutive Jacobsthal numbers), so GCD normalization never reduces them and the total doubles
 * on every split. The total must nevertheless stay bounded and observations must stay possible.
 */
class WeightBoundsTest {
    @Test
    fun totalWeightStaysBoundedAfterManySplits() {
        val game = Game(GameKind.QUANTUM)
        val maxTotal = 1L shl 51
        // Black interleaves pawn pushes with a knight shuffle so that neither the fifty-move rule
        // nor threefold repetition ends the game before the sequence is long enough.
        val pawnPushes = (6 downTo 3).flatMap { rank -> "abcde".map { file -> "$file${rank + 1}$file$rank" } } +
            listOf("g7g6", "g6g5", "g5g4")
        var pawnIndex = 0
        var whiteMoves = 0
        val totalWhiteMoves = 70
        while (whiteMoves < totalWhiteMoves) {
            game.apply(if (whiteMoves % 2 == 0) split("g1", "f3", "g1") else split("f3", "g1", "f3"))
            whiteMoves++
            val state = game.state as QuantumState
            assertTrue(state.totalWeight in 1..maxTotal, "total weight ${state.totalWeight} after white move $whiteMoves")
            assertTrue(state.universes.values.all { it > 0 }, "all weights positive after white move $whiteMoves")
            val probabilities = state.distribution(sq("f3")).values
            assertTrue(probabilities.all { it in 0.0..1.0 }, "probabilities in range after white move $whiteMoves")
            assertEquals(GameStatus.Ongoing, game.status(), "after white move $whiteMoves")
            if (whiteMoves == totalWhiteMoves) break
            val blackMove = when ((whiteMoves - 1) % 3) {
                0 -> pawnPushes[pawnIndex++]
                1 -> "g8h6"
                else -> "h6g8"
            }
            game.apply(normal(blackMove))
        }
        val state = game.state as QuantumState
        QuantumVariant.sampleContent(state, sq("f3"), Random(1))
        val knightOnF3 = state.distribution(sq("f3"))[Piece(Color.WHITE, PieceType.KNIGHT)] ?: 0.0
        assertTrue(knightOnF3 in 0.3..0.7, "the knight should still be in a genuine superposition, got $knightOnF3")
    }
}
