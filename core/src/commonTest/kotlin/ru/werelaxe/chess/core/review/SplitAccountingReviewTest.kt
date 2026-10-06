package ru.werelaxe.chess.core.review

import ru.werelaxe.chess.core.CastlingRights
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.QuantumState
import ru.werelaxe.chess.core.QuantumVariant
import ru.werelaxe.chess.core.board
import ru.werelaxe.chess.core.normal
import ru.werelaxe.chess.core.observe
import ru.werelaxe.chess.core.piece
import ru.werelaxe.chess.core.split
import ru.werelaxe.chess.core.sq
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SplitAccountingReviewTest {
    private fun state(vararg fens: String): QuantumState {
        val universes = fens.associate { board(it) to 1L }
        return QuantumState(universes, 0, mapOf(universes to 1), null)
    }

    @Test
    fun stayWhereTheFirstTargetIsIllegalPassesWithDoubleWeight() {
        val knightOnA1 = "4k3/8/8/8/8/8/8/N3K3 w - - 0 1"
        val knightOnH1 = "4k3/8/8/8/8/8/8/4K2N w - - 0 1"
        val next = QuantumVariant.apply(state(knightOnA1, knightOnH1), split("a1", "b3", "a1"))
        assertEquals(3, next.universeCount)
        assertEquals(4L, next.totalWeight)
        assertEquals(0.25, next.distribution(sq("b3"))[piece('N')])
        assertEquals(0.25, next.distribution(sq("a1"))[piece('N')])
        assertEquals(0.5, next.distribution(sq("h1"))[piece('N')])
    }

    @Test
    fun targetsLegalInDisjointUniversesEachGetDoubleWeight() {
        val b3Blocked = "4k3/8/8/8/3N4/1P6/8/4K3 w - - 0 1"
        val f3Blocked = "4k3/8/8/8/3N4/5P2/8/4K3 w - - 0 1"
        val start = state(b3Blocked, f3Blocked)
        assertTrue(QuantumVariant.isLegal(start, split("d4", "b3", "f3")))
        val next = QuantumVariant.apply(start, split("d4", "b3", "f3"))
        assertEquals(2, next.universeCount)
        assertEquals(2L, next.totalWeight)
        assertEquals(0.5, next.distribution(sq("b3"))[piece('N')])
        assertEquals(0.5, next.distribution(sq("f3"))[piece('N')])
        assertNull(next.distribution(sq("d4"))[piece('N')])
    }

    @Test
    fun passClearsEnPassantAndKeepsCastlingRights() {
        // White: Ke1 Rh1 (may castle) Pe5; Black: Ke8 Pd7. Black splits d7 to d5 or stays.
        val start = QuantumState.of(board("4k3/3p4/8/4P3/8/8/8/4K2R b K - 0 1"))
        val afterSplit = QuantumVariant.apply(start, split("d7", "d5", "d7"))
        val pushed = afterSplit.universes.keys.first { it[sq("d5")] != null }
        assertEquals(sq("d6"), pushed.enPassant)
        // e5xd6 is an en passant capture where the pawn was pushed and impossible where it stayed.
        val captured = QuantumVariant.apply(afterSplit, normal("e5d6"))
        val stayed = captured.universes.keys.first { it[sq("d7")] != null }
        assertEquals(Color.BLACK, stayed.sideToMove)
        assertNull(stayed.enPassant)
        assertEquals(CastlingRights(whiteKingSide = true, whiteQueenSide = false, blackKingSide = false, blackQueenSide = false), stayed.castling)
        val applied = captured.universes.keys.first { it[sq("d6")] != null }
        assertNull(applied[sq("d5")])
        assertEquals(piece('P'), applied[sq("d6")])
    }

    @Test
    fun observationCountsAsAReversiblePly() {
        val start = QuantumState.of(board("4k3/8/8/8/8/8/8/N3K3 w - - 0 1"))
        val afterSplit = QuantumVariant.apply(start, split("a1", "b3", "c2"))
        assertEquals(1, afterSplit.halfmoveClock)
        val observed = QuantumVariant.apply(afterSplit, observe("b3", null))
        assertEquals(2, observed.halfmoveClock)
        assertEquals(Color.WHITE, observed.sideToMove)
        assertEquals(1, observed.universeCount)
        assertEquals(1.0, observed.distribution(sq("c2"))[piece('N')])
    }

    @Test
    fun promotionPieceIsSharedByBothSplitTargets() {
        val start = QuantumState.of(board("1n2k3/P7/8/8/8/8/8/4K3 w - - 0 1"))
        assertTrue(QuantumVariant.requiresPromotion(start, sq("a7"), sq("b8")))
        val split = QuantumVariant.apply(start, split("a7", "a8", "b8", ru.werelaxe.chess.core.PieceType.ROOK))
        assertEquals(0.5, split.distribution(sq("a8"))[piece('R')])
        assertEquals(0.5, split.distribution(sq("b8"))[piece('R')])
        assertEquals(0.5, split.distribution(sq("b8"))[piece('n')])
        assertEquals(0, split.halfmoveClock)
    }
}
