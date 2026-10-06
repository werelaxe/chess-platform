package ru.werelaxe.chess.core.review

import ru.werelaxe.chess.core.Board
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameStatus
import ru.werelaxe.chess.core.QuantumState
import ru.werelaxe.chess.core.QuantumVariant
import ru.werelaxe.chess.core.board
import ru.werelaxe.chess.core.normal
import ru.werelaxe.chess.core.play
import ru.werelaxe.chess.core.split
import kotlin.test.Test
import kotlin.test.assertEquals

/** Threefold repetition is keyed by the whole distribution, so Map<Board, Long> equality must work on every platform. */
class RepetitionReviewTest {
    private val draw = GameStatus.Finished(null, EndReason.THREEFOLD_REPETITION)

    @Test
    fun repetitionOfATwoUniverseDistribution() {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("e2", "e3", "e4"))
        game.apply(normal("a7a6"))
        val shuffle = arrayOf("g1f3", "g8f6", "f3g1", "f6g8")
        game.play(*shuffle)
        assertEquals(GameStatus.Ongoing, game.status())
        game.play(*shuffle)
        assertEquals(draw, game.status())
    }

    @Test
    fun repetitionWithUnequalWeights() {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("g1", "f3", "g1"))
        game.apply(normal("g8f6"))
        game.apply(split("f3", "g1", "f3"))
        game.apply(normal("f6g8"))
        val state = game.state as QuantumState
        assertEquals(setOf(3L, 1L), state.universes.values.toSet())
        val shuffle = arrayOf("b1c3", "b8c6", "c3b1", "c6b8")
        game.play(*shuffle)
        assertEquals(GameStatus.Ongoing, game.status())
        game.play(*shuffle)
        assertEquals(draw, game.status())
    }

    @Test
    fun repetitionWithLargeWeightsIsRecognized() {
        // Weights above 2^32 exercise the high word of Long hash codes on JS.
        val a = board("4k3/8/8/8/8/8/8/N3K3 w - - 0 1")
        val b = board("4k3/8/8/8/8/8/8/4K2N w - - 0 1")
        val universes = mapOf(a to (1L shl 41) + 1, b to (1L shl 41) - 1)
        var state = QuantumState(universes, 0, mapOf(universes to 1), null)
        val shuffle = listOf("e1d1", "e8d8", "d1e1", "d8e8")
        repeat(2) { for (uci in shuffle) state = QuantumVariant.apply(state, normal(uci)) }
        assertEquals(Color.WHITE, state.sideToMove)
        assertEquals(universes, state.universes)
        assertEquals(draw, QuantumVariant.status(state))
    }

    @Test
    fun distributionsReachedInDifferentOrdersCompareEqual() {
        val a = board("4k3/8/8/8/8/8/8/N3K3 w - - 0 1")
        val b = board("4k3/8/8/8/8/8/8/4K2N w - - 0 1")
        val first: Map<Board, Long> = linkedMapOf(a to 2L, b to 5L)
        val second: Map<Board, Long> = linkedMapOf(b to 5L, a to 2L)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(1, mapOf(first to 1)[second])
    }
}
