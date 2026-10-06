package ru.werelaxe.chess.core.review

import ru.werelaxe.chess.core.Board
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.GameStatus
import ru.werelaxe.chess.core.QuantumState
import ru.werelaxe.chess.core.QuantumVariant
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins down whether the quantum fifty-move rule fires at 50 plies (ARCHITECTURE.md 1.2 wording) or 100 plies (RULES.md and the code). */
class FiftyMovePlyThresholdReviewTest {
    private fun withClock(halfmoveClock: Int): QuantumState {
        val initial = QuantumState.initial()
        return QuantumState(initial.universes, halfmoveClock, initial.positionCounts, null)
    }

    @Test
    fun fiftyPliesDoesNotEndTheQuantumGame() {
        assertEquals(GameStatus.Ongoing, QuantumVariant.status(withClock(50)))
        assertEquals(GameStatus.Ongoing, QuantumVariant.status(withClock(99)))
    }

    @Test
    fun oneHundredPliesEndsTheQuantumGame() {
        assertEquals(GameStatus.Finished(null, EndReason.FIFTY_MOVE_RULE), QuantumVariant.status(withClock(100)))
    }
}
