package ru.werelaxe.chess.core.review

import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.GameStatus
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuantumStatusReviewTest {
    private fun state(vararg fens: String): QuantumState {
        val universes = fens.associate { board(it) to 1L }
        return QuantumState(universes, 0, mapOf(universes to 1), null)
    }

    @Test
    fun checkmateInOneUniverseAndStalemateInAnotherIsCheckmate() {
        val mated = "7k/6Q1/5K2/8/8/8/8/8 b - - 0 1"
        val stalemated = "7k/5K2/6Q1/8/8/8/8/8 b - - 0 1"
        assertEquals(GameStatus.Finished(Color.WHITE, EndReason.CHECKMATE), QuantumVariant.status(state(mated, stalemated)))
        assertEquals(GameStatus.Finished(Color.WHITE, EndReason.CHECKMATE), QuantumVariant.status(state(stalemated, mated)))
    }

    @Test
    fun stalemateInOneUniverseAndNoPiecesInAnotherIsStalemate() {
        val stalemated = "7k/5K2/6Q1/8/8/8/8/8 b - - 0 1"
        val kingless = "8/5K2/6Q1/8/8/8/8/8 b - - 0 1"
        assertEquals(GameStatus.Finished(null, EndReason.STALEMATE), QuantumVariant.status(state(stalemated, kingless)))
    }

    @Test
    fun aKinglessSideKeepsPlayingWhereItHasPieces() {
        // Black has no king in one universe but still has a pawn move there; the other universe is a mate.
        val mated = "7k/6Q1/5K2/8/8/8/8/8 b - - 0 1"
        val kingless = "8/6Q1/5K2/8/8/8/p7/8 b - - 0 1"
        val mixed = state(mated, kingless)
        assertEquals(GameStatus.Ongoing, QuantumVariant.status(mixed))
        assertTrue(QuantumVariant.isLegal(mixed, normal("a2a1q")))
        val moved = QuantumVariant.apply(mixed, normal("a2a1q"))
        assertEquals(Color.WHITE, moved.sideToMove)
        assertEquals(0, moved.halfmoveClock)
        assertEquals(0.5, moved.distribution(sq("a1"))[piece('q')])
    }

    @Test
    fun observingAwayTheLastUniverseWithOwnKingLosesByKingCapture() {
        val start = QuantumState.of(board("4k3/8/8/8/8/8/8/4K2R w - - 0 1"))
        val afterSplit = QuantumVariant.apply(start, split("h1", "h8", "h1"))
        val moved = QuantumVariant.apply(afterSplit, normal("e8d8"))
        val captured = QuantumVariant.apply(moved, normal("h8e8"))
        assertEquals(GameStatus.Ongoing, QuantumVariant.status(captured))
        assertTrue(QuantumVariant.canObserve(captured, sq("e8")))
        val collapsed = QuantumVariant.apply(captured, observe("e8", piece('R')))
        assertFalse(collapsed.hasKingAnywhere(Color.BLACK))
        assertEquals(GameStatus.Finished(Color.WHITE, EndReason.KING_CAPTURED), QuantumVariant.status(collapsed))
    }

    @Test
    fun capturingTheOpponentsLastKingWinsImmediately() {
        // Black also owns a pawn on a2, so in the universe where its king is already gone it still has moves.
        val start = QuantumState.of(board("4k3/8/8/8/8/8/p7/4K2R w - - 0 1"))
        val afterSplit = QuantumVariant.apply(start, split("h1", "h8", "h1"))
        val moved = QuantumVariant.apply(afterSplit, normal("e8d8"))
        val captured = QuantumVariant.apply(moved, normal("h8e8"))
        val black = QuantumVariant.apply(captured, normal("d8c8"))
        val check = QuantumVariant.apply(black, normal("h1h8"))
        assertEquals(0.5, QuantumVariant.view(check).checkProbability)
        // Promoting is legal only in the kingless universe; where the king is in check the universe passes.
        val promoted = QuantumVariant.apply(check, normal("a2a1q"))
        assertEquals(GameStatus.Ongoing, QuantumVariant.status(promoted))
        assertTrue(promoted.hasKingAnywhere(Color.BLACK))
        val finished = QuantumVariant.apply(promoted, normal("h8c8"))
        assertFalse(finished.hasKingAnywhere(Color.BLACK))
        assertEquals(GameStatus.Finished(Color.WHITE, EndReason.KING_CAPTURED), QuantumVariant.status(finished))
    }
}
