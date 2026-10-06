package ru.werelaxe.chess.core.review

import ru.werelaxe.chess.core.QuantumState
import ru.werelaxe.chess.core.QuantumVariant
import ru.werelaxe.chess.core.board
import ru.werelaxe.chess.core.normal
import ru.werelaxe.chess.core.split
import ru.werelaxe.chess.core.sq
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PruningReviewTest {
    private val heavy = board("4k3/8/8/8/8/8/8/N3K3 w - - 0 1")
    private val light = board("4k3/8/8/8/8/8/8/4K2N w - - 0 1")

    @Test
    fun negligibleUniversesAreDroppedOnceTheTotalExceedsTheThreshold() {
        val universes = mapOf(heavy to (1L shl 50), light to 1L)
        val state = QuantumState(universes, 0, mapOf(universes to 1), null)
        // The knight on a1 splits in the heavy universe only; the light one passes with double weight.
        val next = QuantumVariant.apply(state, split("a1", "b3", "c2"))
        assertEquals(2, next.universeCount)
        assertTrue(next.universes.keys.none { it[sq("h1")] != null }, "the 2^-50 universe should have been pruned")
        assertEquals(setOf(1L), next.universes.values.toSet())
    }

    @Test
    fun pruningNeverEmptiesTheState() {
        val universes = mapOf(heavy to (1L shl 50) + 1, light to (1L shl 50) - 1)
        val state = QuantumState(universes, 0, mapOf(universes to 1), null)
        val next = QuantumVariant.apply(state, normal("e1d1"))
        assertTrue(next.universeCount >= 1)
        assertTrue(next.totalWeight > 0)
    }
}
