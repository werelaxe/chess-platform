package ru.werelaxe.chess.core.review

import ru.werelaxe.chess.core.ClassicRules
import ru.werelaxe.chess.core.ClassicState
import ru.werelaxe.chess.core.ClassicVariant
import ru.werelaxe.chess.core.Fen
import ru.werelaxe.chess.core.IllegalMoveException
import ru.werelaxe.chess.core.board
import ru.werelaxe.chess.core.mv
import ru.werelaxe.chess.core.normal


import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Defensive behaviour of [Fen.parse] on malformed or inconsistent input. */
class FenRobustnessReviewTest {
    @Test
    fun garbageFenIsRejectedWithIllegalArgumentException() {
        val garbage = listOf(
            "",
            "   ",
            "garbage",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR",
            "9/8/8/8/8/8/8/8 w",
            "8/8/8/8/8/8/8 w",
            "8/8/8/8/8/8/8/8/8 w",
            "x7/8/8/8/8/8/8/8 w",
            "8/8/8/8/8/8/8/8 x",
            "ppppppppp/8/8/8/8/8/8/8 w",
            "0/8/8/8/8/8/8/8 w",
            "44p/8/8/8/8/8/8/8 w",
            "4k3/8/8/8/8/8/8/4K3 w - e9 0 1",
            "4k3/8/8/8/8/8/8/4K3 w - zz 0 1",
        )
        for (fen in garbage) {
            assertFailsWith<IllegalArgumentException>("FEN '$fen' must be rejected") { Fen.parse(fen) }
        }
    }

    @Test
    fun lenientFieldsDoNotCrash() {
        // Missing castling / en passant / clocks fall back to defaults.
        val position = Fen.parse("4k3/8/8/8/8/8/8/4K3 b")
        assertEquals(0, position.halfmoveClock)
        assertEquals(1, position.fullmoveNumber)
        assertNull(position.board.enPassant)
        assertEquals("4k3/8/8/8/8/8/8/4K3 b - - 0 1", Fen.format(position.board))
    }

    @Test
    fun enPassantSquareOnTheWrongRankIsDropped() {
        // White to move with the en passant square on the 3rd rank: only black could ever capture there,
        // so the field is inconsistent and must be ignored (Board.enPassant is documented to be set only
        // when an enemy pawn can actually capture).
        val position = Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq e3 0 1")
        assertNull(position.board.enPassant)
    }

    @Test
    fun phantomEnPassantCannotRemoveAFriendlyPawn() {
        // With the inconsistent "e3" target accepted, d2e3 becomes a "capture" that deletes white's own e2 pawn.
        val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq e3 0 1"
        val board = board(fen)
        assertFalse(ClassicRules.isLegal(board, mv("d2e3")), "d2e3 is not a chess move in the initial position")
        assertFailsWith<IllegalMoveException> { ClassicVariant.apply(ClassicState.fromFen(fen), normal("d2e3")) }
    }

    @Test
    fun enPassantSquareWithoutADoublePushedPawnIsDropped() {
        // White pawn on d5, nothing on e5: e6 cannot be an en passant target.
        val position = Fen.parse("4k3/8/8/3P4/8/8/8/4K3 w - e6 0 1")
        assertNull(position.board.enPassant)
        assertFalse(ClassicRules.isLegal(position.board, mv("d5e6")), "a pawn must not move diagonally onto an empty square")
    }

    @Test
    fun occupiedEnPassantSquareIsDropped() {
        // e6 holds a knight, so no en passant capture onto it is possible; keeping the field breaks
        // Board equality (threefold repetition) and FEN round trips for an otherwise identical position.
        val position = Fen.parse("4k3/8/4n3/3Pp3/8/8/8/4K3 w - e6 0 1")
        assertNull(position.board.enPassant)
    }

    @Test
    fun multipleKingsOfOneColorAreRejected() {
        // Two white kings: only the first one found (a1) is ever considered for check, so the other can be
        // left en prise and captured. Such a placement is invalid in every variant.
        assertFailsWith<IllegalArgumentException> { Fen.parse("4k3/8/8/8/8/8/8/K3K3 w - - 0 1") }
    }

    @Test
    fun classicStateRequiresBothKings() {
        // A kingless universe is legitimate in quantum chess, but a classic game cannot start without kings.
        assertFailsWith<IllegalArgumentException> { ClassicState.fromFen("8/8/8/8/8/8/8/8 w - - 0 1") }
        assertFailsWith<IllegalArgumentException> { ClassicState.fromFen("4k3/8/8/8/8/8/8/8 w - - 0 1") }
    }
}
