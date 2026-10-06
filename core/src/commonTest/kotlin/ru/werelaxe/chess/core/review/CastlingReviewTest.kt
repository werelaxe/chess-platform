package ru.werelaxe.chess.core.review

import ru.werelaxe.chess.core.Board
import ru.werelaxe.chess.core.CastlingRights
import ru.werelaxe.chess.core.ClassicRules
import ru.werelaxe.chess.core.ClassicState
import ru.werelaxe.chess.core.ClassicVariant
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameStatus
import ru.werelaxe.chess.core.Piece
import ru.werelaxe.chess.core.PieceType
import ru.werelaxe.chess.core.board
import ru.werelaxe.chess.core.mv
import ru.werelaxe.chess.core.normal
import ru.werelaxe.chess.core.piece
import ru.werelaxe.chess.core.play
import ru.werelaxe.chess.core.sq
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Review probes for castling rights, paths and attacked squares. */
class CastlingReviewTest {
    private fun targets(fen: String, from: String): Set<String> =
        ClassicRules.legalMoves(board(fen), sq(from)).map { it.to.name }.toSet()

    @Test
    fun kingSideCastlingAllowedWhenOnlyTheRookSquareIsAttacked() {
        // Black rook h8 attacks h1 but neither e1, f1 nor g1.
        assertTrue("g1" in targets("4k2r/8/8/8/8/8/8/4K2R w K - 0 1", "e1"))
    }

    @Test
    fun queenSideCastlingAllowedWhenOnlyB1IsAttackedByBishop() {
        // Bh7 attacks b1 along h7-b1; c1, d1 and e1 are safe.
        assertTrue("c1" in targets("4k3/7b/8/8/8/8/8/R3K2R w KQ - 0 1", "e1"))
    }

    @Test
    fun blackCastlingBothSidesAndB8Subtlety() {
        assertTrue("g8" in targets("r3k2r/8/8/8/8/8/8/4K3 b kq - 0 1", "e8"))
        assertTrue("c8" in targets("r3k2r/8/8/8/8/8/8/4K3 b kq - 0 1", "e8"))
        // b8 attacked by white rook: queen side still allowed. d8 attacked: not allowed.
        assertTrue("c8" in targets("r3k2r/8/8/8/8/8/8/1R2K3 b kq - 0 1", "e8"))
        assertFalse("c8" in targets("r3k2r/8/8/8/8/8/8/3RK3 b kq - 0 1", "e8"))
        // b8 occupied blocks queen side castling even though the king does not cross it.
        assertFalse("c8" in targets("rn2k2r/8/8/8/8/8/8/4K3 b kq - 0 1", "e8"))
        val after = ClassicRules.apply(board("r3k2r/8/8/8/8/8/8/4K3 b kq - 0 1"), mv("e8c8"))
        assertEquals(piece('k'), after[sq("c8")])
        assertEquals(piece('r'), after[sq("d8")])
        assertNull(after[sq("a8")])
        assertEquals(CastlingRights.NONE, after.castling)
    }

    @Test
    fun castlingIntoCheckFromPawnIsIllegal() {
        // Black pawn h2 attacks g1.
        assertFalse("g1" in targets("4k3/8/8/8/8/8/7p/4K2R w K - 0 1", "e1"))
    }

    @Test
    fun castlingThroughCheckFromKnightIsIllegal() {
        // Black knight e3 attacks f1 (and d1).
        val t = targets("4k3/8/8/8/8/4n3/8/R3K2R w KQ - 0 1", "e1")
        assertFalse("g1" in t)
        assertFalse("c1" in t)
    }

    @Test
    fun castlingDeliversCheckmate() {
        val state = ClassicState.fromFen("3k4/2p1p1N1/4B3/8/8/8/8/R3K3 w Q - 0 1")
        assertTrue(ClassicVariant.isLegal(state, normal("e1c1")))
        val after = ClassicVariant.apply(state, normal("e1c1"))
        assertEquals(piece('R'), after.board[sq("d1")])
        assertEquals(GameStatus.Finished(Color.WHITE, EndReason.CHECKMATE), ClassicVariant.status(after))
    }

    @Test
    fun castlingRightNotRestoredWhenAnotherRookRecapturesOnTheCorner() {
        // Bxh1 removes the right; Rh2xh1 brings a rook back to h1 but the right stays lost.
        var board = board("4k3/8/8/8/8/8/6bR/R3K2R b KQ - 0 1")
        board = ClassicRules.apply(board, mv("g2h1"))
        assertFalse(board.castling.whiteKingSide)
        board = ClassicRules.apply(board, mv("h2h1"))
        assertEquals(piece('R'), board[sq("h1")])
        assertFalse(board.castling.whiteKingSide)
        assertTrue(board.castling.whiteQueenSide)
        // Black has to move first; then white must not be able to castle king side.
        board = ClassicRules.apply(board, mv("e8d8"))
        assertFalse("g1" in ClassicRules.legalMoves(board, sq("e1")).map { it.to.name })
        assertTrue("c1" in ClassicRules.legalMoves(board, sq("e1")).map { it.to.name })
    }

    @Test
    fun kingAwayFromHomeSquareNeverCastlesEvenWithStaleRights() {
        val squares = MutableList<Piece?>(64) { null }
        squares[sq("d1").index] = Piece.WHITE_KING
        squares[sq("a1").index] = Piece(Color.WHITE, PieceType.ROOK)
        squares[sq("h1").index] = Piece(Color.WHITE, PieceType.ROOK)
        squares[sq("e8").index] = Piece.BLACK_KING
        val board = Board(squares, Color.WHITE, CastlingRights.ALL, null)
        val t = ClassicRules.legalMoves(board, sq("d1")).map { it.to.name }.toSet()
        assertEquals(setOf("c1", "c2", "d2", "e2", "e1"), t)
    }

    @Test
    fun rookCapturedOnCornerByPromotionRemovesRight() {
        val after = ClassicRules.apply(board("r3k3/1P6/8/8/8/8/8/4K3 w q - 0 1"), mv("b7a8q"))
        assertFalse(after.castling.blackQueenSide)
        assertEquals(piece('Q'), after[sq("a8")])
    }

    @Test
    fun rookTakesRookOnCornerUpdatesBothSides() {
        val after = ClassicRules.apply(board("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"), mv("h1h8"))
        assertEquals(CastlingRights(whiteKingSide = false, whiteQueenSide = true, blackKingSide = false, blackQueenSide = true), after.castling)
    }

    @Test
    fun castlingDoesNotResetHalfmoveClock() {
        val state = ClassicState.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 10 20")
        val after = ClassicVariant.apply(state, normal("e1g1"))
        assertEquals(11, after.halfmoveClock)
        assertEquals(20, after.fullmoveNumber)
    }

    @Test
    fun castlingRightsLostAfterKingMovesAndReturns() {
        val game = Game(GameKind.CLASSIC).play("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "e1e2", "a7a6", "e2e1", "a6a5")
        assertFalse(game.isLegal(normal("e1g1")))
    }

    @Test
    fun requiresPromotionOnlyForLegalPromotingMoves() {
        // a7 pawn is blocked by the knight on a8 but can capture b8; the h7 pawn is blocked by the rook on h8.
        val state = ClassicState.fromFen("nn5r/P6P/8/8/8/8/8/4k2K w - - 0 1")
        assertFalse(ClassicVariant.requiresPromotion(state, sq("a7"), sq("a8")))
        assertTrue(ClassicVariant.requiresPromotion(state, sq("a7"), sq("b8")))
        assertFalse(ClassicVariant.requiresPromotion(state, sq("h7"), sq("h8")))
        assertEquals(setOf(sq("b8")), ClassicVariant.legalTargets(state, sq("a7")))
        assertFalse(ClassicVariant.isLegal(state, normal("a7b8")))
        assertTrue(ClassicVariant.isLegal(state, normal("a7b8n")))
    }

    @Test
    fun castlingMoveEncodedAsKingToRookSquareIsRejected() {
        val board = board("4k3/8/8/8/8/8/8/4K2R w K - 0 1")
        assertFalse(ClassicRules.isLegal(board, mv("e1h1")))
        assertTrue(ClassicRules.isLegal(board, mv("e1g1")))
    }
}
