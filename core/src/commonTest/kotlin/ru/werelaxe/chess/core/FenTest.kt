package ru.werelaxe.chess.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun fenRoundTrips() {
        val fens = listOf(
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
            "rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3",
            "4k3/8/8/8/8/8/8/R3K3 w Q - 5 10",
            "4k3/8/8/8/3pP3/8/8/4K3 b - e3 12 40",
        )
        for (fen in fens) {
            val position = Fen.parse(fen)
            assertEquals(fen, Fen.format(position.board, position.halfmoveClock, position.fullmoveNumber))
            assertEquals(fen, ClassicState.fromFen(fen).fen)
            assertEquals(position.board, Fen.parse(Fen.format(position.board)).board)
        }
    }

    @Test
    fun malformedFenIsRejected() {
        val malformed = listOf(
            "",
            "   ",
            "garbage",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR", // no side to move
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBN w KQkq - 0 1", // 7 files in a rank
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNRR w KQkq - 0 1", // 9 files
            "9/8/8/8/8/8/8/8 w",
            "44p/8/8/8/8/8/8/8 w",
            "0/8/8/8/8/8/8/8 w",
            "8/8/8/8/8/8/8 w", // 7 ranks
            "8/8/8/8/8/8/8/8/8 w", // 9 ranks
            "x7/8/8/8/8/8/8/8 w", // bad piece
            "8/8/8/8/8/8/8/8 x", // bad side
            "4k3/8/8/8/8/8/8/4K3 w - e9 0 1", // bad en passant square
            "4k3/8/8/8/8/8/8/4K3 w - zz 0 1",
        )
        for (fen in malformed) {
            assertFailsWith<IllegalArgumentException>("FEN '$fen' must be rejected") { Fen.parse(fen) }
        }
    }

    @Test
    fun missingFieldsFallBackToDefaults() {
        val position = Fen.parse("4k3/8/8/8/8/8/8/4K3 b")
        assertEquals(0, position.halfmoveClock)
        assertEquals(1, position.fullmoveNumber)
        assertNull(position.board.enPassant)
        assertEquals("4k3/8/8/8/8/8/8/4K3 b - - 0 1", Fen.format(position.board))
    }

    @Test
    fun multipleKingsOfOneColorAreRejected() {
        // Only one king per colour is ever considered for check, so a second one could be left en prise.
        assertFailsWith<IllegalArgumentException> { Fen.parse("4k3/8/8/8/8/8/8/K3K3 w - - 0 1") }
    }

    @Test
    fun classicStateRequiresBothKings() {
        // A kingless universe is legitimate in quantum chess, but a classic game cannot start without kings.
        assertFailsWith<IllegalArgumentException> { ClassicState.fromFen("8/8/8/8/8/8/8/8 w - - 0 1") }
        assertFailsWith<IllegalArgumentException> { ClassicState.fromFen("4k3/8/8/8/8/8/8/8 w - - 0 1") }
    }

    @Test
    fun castlingRightsWithoutRookAreDropped() {
        val position = Fen.parse("4k3/8/8/8/8/8/8/4K2R w KQkq - 0 1")
        assertEquals(CastlingRights(whiteKingSide = true, whiteQueenSide = false, blackKingSide = false, blackQueenSide = false), position.board.castling)
    }

    @Test
    fun usableEnPassantSquareIsKept() {
        val position = Fen.parse("4k3/8/8/8/3pP3/8/8/4K3 b - e3 0 1")
        assertEquals(sq("e3"), position.board.enPassant)
    }

    @Test
    fun unusableEnPassantSquareIsDropped() {
        val position = Fen.parse("4k3/8/8/8/4P3/8/8/4K3 b - e3 0 1")
        assertNull(position.board.enPassant)
    }

    @Test
    fun enPassantSquareOnTheWrongRankIsDroppedAndCannotBeCaptured() {
        // White to move with the en passant square on the 3rd rank: only black could ever capture there.
        // Accepting the field would turn d2e3 into a "capture" that deletes white's own e2 pawn.
        val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq e3 0 1"
        val board = board(fen)
        assertNull(board.enPassant)
        assertFalse(ClassicRules.isLegal(board, mv("d2e3")), "d2e3 is not a chess move in the initial position")
        assertEquals(setOf("d3", "d4"), ClassicRules.legalMoves(board, sq("d2")).map { it.to.name }.toSet())
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
        // e6 holds a knight, so no en passant capture onto it is possible; the position must equal the
        // same FEN with "-", otherwise Board equality (threefold repetition) and FEN round trips break.
        val withEp = board("4k3/8/4n3/3Pp3/8/8/8/4K3 w - e6 0 1")
        val without = board("4k3/8/4n3/3Pp3/8/8/8/4K3 w - - 0 1")
        assertNull(withEp.enPassant)
        assertEquals(without, withEp)
        assertEquals(without.hashCode(), withEp.hashCode())
    }

    @Test
    fun squareParsingAndIndexing() {
        assertEquals("a1", Square(0).name)
        assertEquals("h8", Square(63).name)
        assertEquals(0, sq("a1").index)
        assertEquals(7, sq("h1").index)
        assertEquals(56, sq("a8").index)
        assertEquals(63, sq("h8").index)
        assertEquals(28, sq("e4").index)
        assertEquals("e4", Square(28).name)
        assertEquals(4, sq("e4").file)
        assertEquals(3, sq("e4").rank)
        assertEquals(Square.of(4, 3), sq("e4"))
        for (bad in listOf("i1", "a9", "a0", "A1", "", "e44", "4e", "e", "h9")) {
            assertFailsWith<IllegalArgumentException>("'$bad' must be rejected") { Square.parse(bad) }
        }
        assertFailsWith<IllegalArgumentException> { Square(64) }
        assertFailsWith<IllegalArgumentException> { Square(-1) }
        assertNull(sq("a1").offset(-1, 0))
        assertNull(sq("h8").offset(0, 1))
        assertEquals(sq("b2"), sq("a1").offset(1, 1))
    }

    @Test
    fun moveUciParsing() {
        assertEquals(Move(sq("e2"), sq("e4")), mv("e2e4"))
        assertEquals(Move(sq("e7"), sq("e8"), PieceType.QUEEN), mv("e7e8q"))
        assertEquals(Move(sq("e7"), sq("e8"), PieceType.QUEEN), mv("e7e8Q"))
        assertEquals("e7e8q", mv("e7e8q").uci)
        assertEquals("e7e8n", mv("e7e8n").uci)
        for (bad in listOf("e2e", "e2e4e5", "e7e8x", "", "e2 e4")) {
            assertFailsWith<IllegalArgumentException>("'$bad' must be rejected") { Move.parseUci(bad) }
        }
    }

    @Test
    fun squaresBehaveAsValuesInsideCollections() {
        // Squares must hash and compare by value in collections on every platform.
        val set = LinkedHashSet<Square>()
        set.add(Square(27))
        set.add(sq("d4"))
        set.add(Square.of(3, 3))
        assertEquals(1, set.size)
        assertTrue(Square.parse("d4") in set)
        val map = HashMap<Square, Int>()
        map[Square(0)] = 1
        map[sq("a1")] = 2
        assertEquals(mapOf(Square.A1 to 2), map)
        assertEquals(Square.ALL.toSet(), (0 until 64).map { Square(it) }.toSet())
        assertEquals(listOf(27), listOf(sq("d4")).map { it.index })
    }

    @Test
    fun boardEqualityAndHashAfterApplyMatchesParsedFen() {
        var board = Board.initial()
        for (uci in listOf("e2e4", "d7d5", "e4e5", "f7f5", "e1e2", "e8d7", "e2e1", "d7e8")) {
            board = ClassicRules.apply(board, mv(uci))
            val reparsed = Fen.parse(Fen.format(board)).board
            assertEquals(board, reparsed)
            assertEquals(board.hashCode(), reparsed.hashCode())
        }
        // A board reached by play equals one parsed from a hand-written FEN, en passant square included.
        var played = Board.initial()
        for (uci in listOf("e2e4", "a7a6", "e4e5", "d7d5")) played = ClassicRules.apply(played, mv(uci))
        val parsed = board("rnbqkbnr/1pp1pppp/p7/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3")
        assertEquals(sq("d6"), played.enPassant)
        assertEquals(parsed, played)
        assertEquals(parsed.hashCode(), played.hashCode())
        assertEquals(Fen.format(parsed), Fen.format(played))
    }

    @Test
    fun boardsDifferingOnlyBySideCastlingOrEnPassantAreNotEqual() {
        val base = board("4k3/8/8/3pP3/8/8/8/R3K3 w Q d6 0 1")
        assertNotEquals(base, base.copy(sideToMove = Color.BLACK))
        assertNotEquals(base, base.copy(castling = base.castling.without(Color.WHITE)))
        assertNotEquals(base, base.copy(enPassant = null))
        assertEquals(base, base.copy())
        assertEquals(base.hashCode(), base.copy().hashCode())
    }
}
