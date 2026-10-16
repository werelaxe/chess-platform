package ru.werelaxe.chess.engine

import ru.werelaxe.chess.core.ClassicState
import ru.werelaxe.chess.core.QuantumState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UniverseCeilingTest {
    @Test
    fun restrictsAPositionAboveTheCeilingToItsMostProbableUniverses() {
        val fog = quantumPosition(300)
        assertNull(UniverseCeiling(4096).restrict(fog), "within the ceiling the position is searched as it stands")
        assertNull(UniverseCeiling(0).restrict(fog), "a ceiling of zero is none")
        assertNull(UniverseCeiling(1).restrict(ClassicState.initial()), "classic chess has no universes")

        val restricted = assertNotNull(UniverseCeiling(100).restrict(fog))
        assertEquals(100, restricted.universeCount, "never more than the ceiling")
        assertEquals(fog.sideToMove, restricted.sideToMove)
        val heaviest = fog.universes.entries.sortedByDescending { it.value }.take(100).map { it.key }.toSet()
        assertEquals(heaviest, restricted.universes.keys)

        val wide = assertNotNull(UniverseCeiling(299).restrict(fog))
        assertEquals(UniverseCeiling.SHALLOW_UNIVERSES, wide.universeCount, "never more than the shallow search affords")
        assertNull(UniverseCeiling(1).restrict(QuantumState.initial()))
        assertFailsWith<IllegalArgumentException> { UniverseCeiling(-1) }
    }
}
