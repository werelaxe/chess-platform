package ru.werelaxe.chess.core.review

import ru.werelaxe.chess.core.ClassicRules
import ru.werelaxe.chess.core.ClassicState
import ru.werelaxe.chess.core.ClassicVariant
import ru.werelaxe.chess.core.Fen
import ru.werelaxe.chess.core.IllegalMoveException
import ru.werelaxe.chess.core.board
import ru.werelaxe.chess.core.mv
import ru.werelaxe.chess.core.normal
import ru.werelaxe.chess.core.piece
import ru.werelaxe.chess.core.sq
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Review probes for the en passant field of FEN parsing. [Fen.parse] only checks that a pawn of
 * the side to move stands diagonally behind the en passant square; it does not check the rank of
 * the square, that an enemy pawn actually stands in front of it, or that the square is empty.
 */
class FenEnPassantReviewTest {
    @Test
    fun enPassantSquareOnWrongRankForSideToMoveIsRejected() {
        // White to move: "e3" can only be an en passant target for black. The white pawn on d2 would get a
        // phantom "en passant" capture onto e3 that deletes white's own queen on e2.
        val position = Fen.parse("4k3/8/8/8/8/8/3PQ3/4K3 w - e3 0 1")
        assertNull(position.board.enPassant, "en passant square on the wrong rank must be dropped")
        assertFalse(ClassicRules.isLegal(position.board, mv("d2e3")), "d2e3 is not a legal pawn move")
        assertEquals(piece('Q'), position.board[sq("e2")])
    }

    @Test
    fun phantomEnPassantMustNotDeleteOwnPiece() {
        val state = ClassicState.fromFen("4k3/8/8/8/8/8/3PQ3/4K3 w - e3 0 1")
        val targets = ClassicRules.legalMoves(state.board, sq("d2")).map { it.to.name }.toSet()
        assertEquals(setOf("d3", "d4"), targets)
        // Today ClassicVariant.apply accepts d2e3 and the resulting board has lost the white queen on e2.
        assertFailsWith<IllegalMoveException> { ClassicVariant.apply(state, normal("d2e3")) }
    }

    @Test
    fun enPassantSquareWithoutDoublePushedPawnIsRejected() {
        // Black to move, black pawn d4 is diagonally adjacent to e3, but there is no white pawn on e4 to capture.
        val position = Fen.parse("4k3/8/8/8/3p4/8/8/4K3 b - e3 0 1")
        assertNull(position.board.enPassant, "nothing can be captured en passant")
        assertFalse(ClassicRules.isLegal(position.board, mv("d4e3")))
        assertEquals(setOf("d3"), ClassicRules.legalMoves(position.board, sq("d4")).map { it.to.name }.toSet())
    }

    @Test
    fun occupiedEnPassantSquareIsDropped() {
        // e3 holds a white knight: the pawn on d4 captures it normally, no en passant capture exists,
        // so the position must equal the same FEN with "-" (documented Board invariant).
        val withEp = board("4k3/8/8/8/3pP3/4N3/8/4K3 b - e3 0 1")
        val without = board("4k3/8/8/8/3pP3/4N3/8/4K3 b - - 0 1")
        assertNull(withEp.enPassant)
        assertEquals(without, withEp)
        assertEquals(without.hashCode(), withEp.hashCode())
    }

    @Test
    fun validEnPassantSquareIsKeptAndWorks() {
        val board = board("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1")
        assertEquals(sq("d6"), board.enPassant)
        assertTrue(ClassicRules.isLegal(board, mv("e5d6")))
        val after = ClassicRules.apply(board, mv("e5d6"))
        assertNull(after[sq("d5")])
        assertNull(after[sq("e5")])
        assertEquals(piece('P'), after[sq("d6")])
        assertNull(after.enPassant)
    }

    @Test
    fun enPassantSquareWithoutCapturingPawnIsDroppedAndRoundTripsAsDash() {
        val position = Fen.parse("rnbqkbnr/pppp1ppp/8/4p3/8/8/PPPPPPPP/RNBQKBNR w KQkq e6 0 2")
        assertNull(position.board.enPassant)
        assertEquals("rnbqkbnr/pppp1ppp/8/4p3/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 2", Fen.format(position.board, 0, 2))
    }
}
