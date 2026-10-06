package ru.werelaxe.chess.core.review

import ru.werelaxe.chess.core.Board
import ru.werelaxe.chess.core.ChessJson
import ru.werelaxe.chess.core.ClassicRules
import ru.werelaxe.chess.core.ClassicState
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.Fen
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.GameStatus
import ru.werelaxe.chess.core.Move
import ru.werelaxe.chess.core.PieceType
import ru.werelaxe.chess.core.Square
import ru.werelaxe.chess.core.board
import ru.werelaxe.chess.core.mv
import ru.werelaxe.chess.core.normal
import ru.werelaxe.chess.core.sq
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** Review probes for Square/Move parsing, FEN round trips, JSON and Board equality/hashCode. */
class ParsingAndEqualityReviewTest {
    @Test
    fun squareParsingAndIndexing() {
        assertEquals(0, sq("a1").index)
        assertEquals(7, sq("h1").index)
        assertEquals(56, sq("a8").index)
        assertEquals(63, sq("h8").index)
        assertEquals(28, sq("e4").index)
        assertEquals("e4", Square(28).name)
        assertEquals(4, sq("e4").file)
        assertEquals(3, sq("e4").rank)
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
        assertEquals("e7e8n", mv("e7e8n").uci)
        for (bad in listOf("e2e", "e2e4e5", "e7e8x", "", "e2 e4")) {
            assertFailsWith<IllegalArgumentException>("'$bad' must be rejected") { Move.parseUci(bad) }
        }
    }

    @Test
    fun fenRoundTrips() {
        val fens = listOf(
            Fen.INITIAL,
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
    fun fenParsingValidation() {
        for (bad in listOf(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBN w KQkq - 0 1",      // 7 files in a rank
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNRR w KQkq - 0 1",    // 9 files
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1",              // 7 ranks
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1",     // bad side
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq z9 0 1",    // bad ep square
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNX w KQkq - 0 1",     // bad piece
            "",
        )) {
            assertFailsWith<IllegalArgumentException>("'$bad' must be rejected") { Fen.parse(bad) }
        }
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
        var ep = Board.initial()
        for (uci in listOf("e2e4", "d7d5", "e4e5", "f7f5")) ep = ClassicRules.apply(ep, mv(uci))
        assertEquals(sq("f6"), ep.enPassant)
        assertEquals(ep, Fen.parse(Fen.format(ep)).board)
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

    @Test
    fun unusableEnPassantMakesBoardsEqualAndFormatsAsDash() {
        val after = ClassicRules.apply(Board.initial(), mv("e2e4"))
        assertNull(after.enPassant)
        assertEquals(board("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"), after)
        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1", Fen.format(after))
    }

    @Test
    fun jsonRoundTrips() {
        assertEquals("""{"type":"normal","from":"e2","to":"e4"}""", ChessJson.encodeMove(normal("e2e4")))
        assertEquals("""{"type":"normal","from":"e7","to":"e8","promotion":"QUEEN"}""", ChessJson.encodeMove(normal("e7e8q")))
        assertEquals(normal("e7e8q"), ChessJson.decodeMove("""{"type":"normal","from":"e7","to":"e8","promotion":"QUEEN"}"""))
        assertEquals(normal("e2e4"), ChessJson.decodeMove("""{"type":"normal","from":"e2","to":"e4","promotion":null}"""))
        assertEquals("""{"type":"ongoing"}""", ChessJson.encodeStatus(GameStatus.Ongoing))
        assertEquals("""{"type":"finished","winner":null,"reason":"STALEMATE"}""", ChessJson.encodeStatus(GameStatus.Finished(null, EndReason.STALEMATE)))
        assertEquals("""{"type":"finished","winner":"WHITE","reason":"CHECKMATE"}""", ChessJson.encodeStatus(GameStatus.Finished(Color.WHITE, EndReason.CHECKMATE)))
        val moves = listOf<GameMove>(normal("e2e4"), normal("e7e8q"))
        assertEquals(moves, ChessJson.decodeMoves(ChessJson.encodeMoves(moves)))
        assertFailsWith<Exception> { ChessJson.decodeMove("""{"type":"normal","from":"e9","to":"e4"}""") }
    }
}
