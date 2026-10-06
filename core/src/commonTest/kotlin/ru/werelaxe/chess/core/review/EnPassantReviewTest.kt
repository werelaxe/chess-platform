package ru.werelaxe.chess.core.review

import ru.werelaxe.chess.core.ClassicRules
import ru.werelaxe.chess.core.ClassicState
import ru.werelaxe.chess.core.ClassicVariant
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.GameStatus
import ru.werelaxe.chess.core.board
import ru.werelaxe.chess.core.mv
import ru.werelaxe.chess.core.normal
import ru.werelaxe.chess.core.sq
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Review probes for en passant legality (pins in every direction) and the en passant square semantics. */
class EnPassantReviewTest {
    @Test
    fun enPassantIllegalWhenCapturingPawnIsPinnedOnTheFile() {
        // Black pawn d4 is pinned by Rd1 against Kd8; after e2-e4 the capture d4xe3 would expose the king.
        val after = ClassicRules.apply(board("3k4/8/8/8/3p4/8/4P3/3RK3 w - - 0 1"), mv("e2e4"))
        assertFalse(ClassicRules.isLegal(after, mv("d4e3")))
        assertTrue(ClassicRules.isLegal(after, mv("d8d7")))
    }

    @Test
    fun enPassantIllegalWhenCapturingPawnIsPinnedOnTheLongDiagonal() {
        // Black pawn d4 is pinned by Ba1 against Kh8 (a1-h8 diagonal); d4xe3 leaves the diagonal.
        val after = ClassicRules.apply(board("7k/8/8/8/3p4/8/4P3/B3K3 w - - 0 1"), mv("e2e4"))
        assertNull(after.enPassant, "an en passant square that cannot legally be used is not recorded")
        assertFalse(ClassicRules.isLegal(after, mv("d4e3")))
    }

    @Test
    fun enPassantLegalWhenCapturingPawnIsPinnedAlongTheCaptureDiagonal() {
        // Black pawn d4 is pinned by Bg1 against Ka7 along a7-g1; d4xe3 stays on that diagonal, so it is legal.
        val after = ClassicRules.apply(board("8/k7/8/8/3p4/8/4P3/4K1B1 w - - 0 1"), mv("e2e4"))
        assertEquals(sq("e3"), after.enPassant)
        assertTrue(ClassicRules.isLegal(after, mv("d4e3")))
    }

    @Test
    fun enPassantIllegalWhileInCheckFromAnotherPiece() {
        // After e2-e4 the bishop f1 gives discovered check to Ka6; d4xe3 does not address it.
        val after = ClassicRules.apply(board("8/8/k7/8/3p4/8/4P3/4KB2 w - - 0 1"), mv("e2e4"))
        assertTrue(ClassicRules.isInCheck(after, Color.BLACK))
        assertFalse(ClassicRules.isLegal(after, mv("d4e3")))
    }

    @Test
    fun onlyLegalMoveBeingEnPassantIsNotStalemate() {
        // Black Kh1 has no king moves, c4 pawn is blocked, but c4xd3 e.p. is available.
        val state = ClassicState.fromFen("8/8/8/8/2pP4/2P5/5K2/5N1k b - d3 0 1")
        assertEquals(sq("d3"), state.board.enPassant)
        assertEquals(listOf(mv("c4d3")), ClassicRules.legalMoves(state.board))
        assertEquals(GameStatus.Ongoing, ClassicVariant.status(state))
    }

    @Test
    fun enPassantCaptureResetsHalfmoveClockAndCountsAsCapture() {
        val state = ClassicState.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 42 30")
        assertTrue(ClassicRules.isCapture(state.board, mv("e5d6")))
        val after = ClassicVariant.apply(state, normal("e5d6"))
        assertEquals(0, after.halfmoveClock)
        assertNull(after.board[sq("d5")])
    }

    @Test
    fun enPassantSquareNotSetWhenNoEnemyPawnIsAdjacent() {
        val after = ClassicRules.apply(board("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1"), mv("e2e4"))
        assertNull(after.enPassant)
        assertEquals(board("4k3/8/8/8/4P3/8/8/4K3 b - - 0 1"), after)
    }

    @Test
    fun enPassantSquareNotSetAtBoardEdge() {
        // a2-a4 with a black pawn on b4: capturable. h2-h4 with nothing on g4: not.
        assertEquals(sq("a3"), ClassicRules.apply(board("4k3/8/8/8/1p6/8/P7/4K3 w - - 0 1"), mv("a2a4")).enPassant)
        assertNull(ClassicRules.apply(board("4k3/8/8/8/8/8/7P/4K3 w - - 0 1"), mv("h2h4")).enPassant)
    }

    // Spec (ARCHITECTURE.md 1.1): "Board.enPassant is set only when an enemy pawn can actually capture, so
    // positions that differ only by an unusable en passant square are equal." A pinned pawn cannot capture,
    // so the position after e2-e4 is the same position (FIDE 9.2.2: same possible moves) as the one reached
    // later by king shuffles. Under FIDE the third occurrence is reached after the 9th ply below.
    @Test
    fun threefoldRepetitionCountsPositionWhereEnPassantIsOnlyPseudoLegal() {
        var state = ClassicState.fromFen("3k4/8/8/8/3p4/8/4P3/3RK3 w - - 0 1")
        val plies = listOf("e2e4", "d8d7", "e1f1", "d7d8", "f1e1", "d8d7", "e1f1", "d7d8", "f1e1")
        for (ply in plies) state = ClassicVariant.apply(state, normal(ply))
        // Position after e2e4 (black to move, d4xe3 pinned) and after each f1e1 are the same FIDE position.
        assertEquals(GameStatus.Finished(null, EndReason.THREEFOLD_REPETITION), ClassicVariant.status(state))
    }
}
