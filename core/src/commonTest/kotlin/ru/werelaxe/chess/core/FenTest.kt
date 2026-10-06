package ru.werelaxe.chess.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FenTest {
    @Test
    fun initialRoundTrip() {
        val position = Fen.parse(Fen.INITIAL)
        assertEquals(Fen.INITIAL, Fen.format(position.board, position.halfmoveClock, position.fullmoveNumber))
        assertEquals(Piece.WHITE_KING, position.board[sq("e1")])
        assertEquals(Piece.BLACK_KING, position.board[sq("e8")])
        assertEquals(piece('p'), position.board[sq("a7")])
    }

    @Test
    fun unusableEnPassantSquareIsDropped() {
        val position = Fen.parse("4k3/8/8/8/4P3/8/8/4K3 b - e3 0 1")
        assertNull(position.board.enPassant)
    }

    @Test
    fun usableEnPassantSquareIsKept() {
        val position = Fen.parse("4k3/8/8/8/3pP3/8/8/4K3 b - e3 0 1")
        assertEquals(sq("e3"), position.board.enPassant)
    }

    @Test
    fun castlingRightsWithoutRookAreDropped() {
        val position = Fen.parse("4k3/8/8/8/8/8/8/4K2R w KQkq - 0 1")
        assertEquals(CastlingRights(whiteKingSide = true, whiteQueenSide = false, blackKingSide = false, blackQueenSide = false), position.board.castling)
    }

    @Test
    fun squareNaming() {
        assertEquals("a1", Square(0).name)
        assertEquals("h8", Square(63).name)
        assertEquals(Square.of(4, 3), sq("e4"))
        assertEquals("e7e8q", mv("e7e8q").uci)
    }
}
