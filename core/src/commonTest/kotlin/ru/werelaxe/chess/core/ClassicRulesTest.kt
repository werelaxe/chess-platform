package ru.werelaxe.chess.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClassicRulesTest {
    private fun targets(fen: String, from: String): Set<String> =
        ClassicRules.legalMoves(board(fen), sq(from)).map { it.to.name }.toSet()

    private fun apply(fen: String, vararg ucis: String): ClassicState {
        var state = ClassicState.fromFen(fen)
        for (uci in ucis) state = ClassicVariant.apply(state, normal(uci))
        return state
    }

    @Test
    fun enPassantCaptureEscapesCheckFromDoublePushedPawn() {
        // White Kd4 Pb5, Black Kh8 Pc7. Black pushes c7-c5 with check; b5xc6 e.p. is the escape.
        val before = board("7k/2p5/8/1P6/3K4/8/8/8 b - - 0 1")
        val after = ClassicRules.apply(before, mv("c7c5"))
        assertEquals(sq("c6"), after.enPassant)
        assertTrue(ClassicRules.isInCheck(after, Color.WHITE))
        assertTrue(ClassicRules.isLegal(after, mv("b5c6")))
        val captured = ClassicRules.apply(after, mv("b5c6"))
        assertNull(captured[sq("c5")])
        assertEquals(piece('P'), captured[sq("c6")])
        assertFalse(ClassicRules.isInCheck(captured, Color.WHITE))
    }

    @Test
    fun enPassantIsIllegalWhenItExposesKingAlongTheRank() {
        // White Ka5 Pb5, Black Pc5 (just double pushed) Rh5: both pawns leaving the rank exposes the king.
        val board = board("7k/8/8/KPp4r/8/8/8/8 w - c6 0 1")
        assertNull(board.enPassant, "a pinned en passant capture is not recorded as available")
        assertFalse(ClassicRules.isLegal(board, mv("b5c6")))
        val playedOut = ClassicRules.apply(board("7k/2p5/8/KP5r/8/8/8/8 b - - 0 1"), mv("c7c5"))
        assertNull(playedOut.enPassant)
        assertFalse(ClassicRules.isLegal(playedOut, mv("b5c6")))
    }

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
    fun enPassantOnlyRightAfterTheDoublePush() {
        val game = Game(GameKind.CLASSIC).play("e2e4", "a7a6", "e4e5", "d7d5")
        assertTrue(game.isLegal(normal("e5d6")))
        game.play("h2h3", "h7h6")
        assertFalse(game.isLegal(normal("e5d6")))
    }

    @Test
    fun enPassantSquareIsSetOnlyWhenAnEnemyPawnCanCapture() {
        // After e2-e4 from the initial position no black pawn can capture on e3, so the position equals
        // (and formats as) the same FEN with "-".
        val opening = ClassicRules.apply(Board.initial(), mv("e2e4"))
        assertNull(opening.enPassant)
        assertEquals(board("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"), opening)
        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1", Fen.format(opening))
        val lone = ClassicRules.apply(board("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1"), mv("e2e4"))
        assertNull(lone.enPassant)
        assertEquals(board("4k3/8/8/8/4P3/8/8/4K3 b - - 0 1"), lone)
        // a2-a4 with a black pawn on b4: capturable. h2-h4 with nothing on g4: not.
        assertEquals(sq("a3"), ClassicRules.apply(board("4k3/8/8/8/1p6/8/P7/4K3 w - - 0 1"), mv("a2a4")).enPassant)
        assertNull(ClassicRules.apply(board("4k3/8/8/8/8/8/7P/4K3 w - - 0 1"), mv("h2h4")).enPassant)
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
    fun threefoldRepetitionCountsPositionWhereEnPassantIsOnlyPseudoLegal() {
        // ARCHITECTURE.md 1.1: Board.enPassant is set only when an enemy pawn can actually capture, so
        // positions that differ only by an unusable en passant square are equal. A pinned pawn cannot
        // capture, so the position after e2-e4 is the same position (FIDE 9.2.2: same possible moves)
        // as the one reached later by king shuffles; the third occurrence is reached on the 9th ply.
        val state = apply(
            "3k4/8/8/8/3p4/8/4P3/3RK3 w - - 0 1",
            "e2e4", "d8d7", "e1f1", "d7d8", "f1e1", "d8d7", "e1f1", "d7d8", "f1e1",
        )
        assertEquals(GameStatus.Finished(null, EndReason.THREEFOLD_REPETITION), ClassicVariant.status(state))
    }

    @Test
    fun castlingBothSides() {
        val board = board("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        assertEquals(setOf("d1", "d2", "e2", "f2", "f1", "g1", "c1"), targets("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1", "e1"))
        val kingSide = ClassicRules.apply(board, mv("e1g1"))
        assertEquals(piece('K'), kingSide[sq("g1")])
        assertEquals(piece('R'), kingSide[sq("f1")])
        assertNull(kingSide[sq("h1")])
        assertEquals(CastlingRights.NONE, kingSide.castling)
        val queenSide = ClassicRules.apply(board, mv("e1c1"))
        assertEquals(piece('K'), queenSide[sq("c1")])
        assertEquals(piece('R'), queenSide[sq("d1")])
        assertNull(queenSide[sq("a1")])
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
    fun castlingThroughAttackedSquareIsIllegal() {
        assertFalse("g1" in targets("5r1k/8/8/8/8/8/8/R3K2R w KQ - 0 1", "e1"))
        assertTrue("c1" in targets("5r1k/8/8/8/8/8/8/R3K2R w KQ - 0 1", "e1"))
    }

    @Test
    fun castlingThroughCheckFromKnightIsIllegal() {
        // Black knight e3 attacks f1 (and d1).
        val t = targets("4k3/8/8/8/8/4n3/8/R3K2R w KQ - 0 1", "e1")
        assertFalse("g1" in t)
        assertFalse("c1" in t)
    }

    @Test
    fun castlingIntoCheckFromPawnIsIllegal() {
        // Black pawn h2 attacks g1.
        assertFalse("g1" in targets("4k3/8/8/8/8/8/7p/4K2R w K - 0 1", "e1"))
    }

    @Test
    fun queenSideCastlingAllowedWhenOnlyB1IsAttacked() {
        assertTrue("c1" in targets("1r5k/8/8/8/8/8/8/R3K2R w KQ - 0 1", "e1"))
        // Bh7 attacks b1 along h7-b1; c1, d1 and e1 are safe.
        assertTrue("c1" in targets("4k3/7b/8/8/8/8/8/R3K2R w KQ - 0 1", "e1"))
        assertFalse("c1" in targets("3r3k/8/8/8/8/8/8/R3K2R w KQ - 0 1", "e1"))
    }

    @Test
    fun kingSideCastlingAllowedWhenOnlyTheRookSquareIsAttacked() {
        // Black rook h8 attacks h1 but neither e1, f1 nor g1.
        assertTrue("g1" in targets("4k2r/8/8/8/8/8/8/4K2R w K - 0 1", "e1"))
    }

    @Test
    fun castlingOutOfCheckIsIllegal() {
        val t = targets("4r2k/8/8/8/8/8/8/R3K2R w KQ - 0 1", "e1")
        assertFalse("g1" in t)
        assertFalse("c1" in t)
    }

    @Test
    fun castlingBlockedByPieces() {
        assertFalse("g1" in targets("4k3/8/8/8/8/8/8/R3KB1R w KQ - 0 1", "e1"))
        assertFalse("c1" in targets("4k3/8/8/8/8/8/8/RN2K2R w KQ - 0 1", "e1"))
    }

    @Test
    fun castlingMoveEncodedAsKingToRookSquareIsRejected() {
        val board = board("4k3/8/8/8/8/8/8/4K2R w K - 0 1")
        assertFalse(ClassicRules.isLegal(board, mv("e1h1")))
        assertTrue(ClassicRules.isLegal(board, mv("e1g1")))
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
    fun castlingDoesNotResetHalfmoveClock() {
        val after = apply("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 10 20", "e1g1")
        assertEquals(11, after.halfmoveClock)
        assertEquals(20, after.fullmoveNumber)
    }

    @Test
    fun castlingRightsLostAfterKingOrRookMoves() {
        val rookMoved = Game(GameKind.CLASSIC).play("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6")
        assertTrue(rookMoved.isLegal(normal("e1g1")))
        rookMoved.play("h1g1", "a7a6", "g1h1", "a6a5")
        assertFalse(rookMoved.isLegal(normal("e1g1")))
        val kingMoved = Game(GameKind.CLASSIC).play("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "e1e2", "a7a6", "e2e1", "a6a5")
        assertFalse(kingMoved.isLegal(normal("e1g1")))
    }

    @Test
    fun castlingRightLostWhenRookIsCapturedAndNotRestoredByRecapture() {
        // Bxh1 removes the right; Rh2xh1 brings a rook back to h1 but the right stays lost.
        var board = board("4k3/8/8/8/8/8/6bR/R3K2R b KQ - 0 1")
        board = ClassicRules.apply(board, mv("g2h1"))
        assertFalse(board.castling.whiteKingSide)
        assertTrue(board.castling.whiteQueenSide)
        board = ClassicRules.apply(board, mv("h2h1"))
        assertEquals(piece('R'), board[sq("h1")])
        assertFalse(board.castling.whiteKingSide)
        assertTrue(board.castling.whiteQueenSide)
        board = ClassicRules.apply(board, mv("e8d8"))
        assertFalse("g1" in ClassicRules.legalMoves(board, sq("e1")).map { it.to.name })
        assertTrue("c1" in ClassicRules.legalMoves(board, sq("e1")).map { it.to.name })
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
    fun promotionRequiresPieceChoice() {
        val board = board("4k3/P7/8/8/8/8/8/4K3 w - - 0 1")
        assertFalse(ClassicRules.isLegal(board, mv("a7a8")))
        for (promotion in PieceType.PROMOTIONS) assertTrue(ClassicRules.isLegal(board, Move(sq("a7"), sq("a8"), promotion)))
        assertFalse(ClassicRules.isLegal(board, Move(sq("a7"), sq("a8"), PieceType.KING)))
        assertEquals(piece('N'), ClassicRules.apply(board, mv("a7a8n"))[sq("a8")])
        assertTrue(ClassicVariant.requiresPromotion(ClassicState.fromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1"), sq("a7"), sq("a8")))
    }

    @Test
    fun promotionByCapture() {
        val board = board("1n2k3/P7/8/8/8/8/8/4K3 w - - 0 1")
        assertTrue(ClassicRules.isLegal(board, mv("a7b8q")))
        assertEquals(piece('Q'), ClassicRules.apply(board, mv("a7b8q"))[sq("b8")])
    }

    @Test
    fun requiresPromotionOnlyForLegalPromotingMoves() {
        // The a7 pawn is blocked by the knight on a8 but can capture b8; the h7 pawn is blocked by the rook on h8.
        val state = ClassicState.fromFen("nn5r/P6P/8/8/8/8/8/4k2K w - - 0 1")
        assertFalse(ClassicVariant.requiresPromotion(state, sq("a7"), sq("a8")))
        assertTrue(ClassicVariant.requiresPromotion(state, sq("a7"), sq("b8")))
        assertFalse(ClassicVariant.requiresPromotion(state, sq("h7"), sq("h8")))
        assertEquals(setOf(sq("b8")), ClassicVariant.legalTargets(state, sq("a7")))
        assertFalse(ClassicVariant.isLegal(state, normal("a7b8")))
        assertTrue(ClassicVariant.isLegal(state, normal("a7b8n")))
    }

    @Test
    fun foolsMateIsCheckmate() {
        val game = Game(GameKind.CLASSIC).play("f2f3", "e7e5", "g2g4", "d8h4")
        assertEquals(GameStatus.Finished(Color.BLACK, EndReason.CHECKMATE), game.status())
        assertFalse(game.isLegal(normal("a2a3")))
    }

    @Test
    fun smotheredMate() {
        val smothered = ClassicState.fromFen("6rk/5Npp/8/8/8/8/8/4K3 b - - 0 1")
        assertEquals(GameStatus.Finished(Color.WHITE, EndReason.CHECKMATE), ClassicVariant.status(smothered))
    }

    @Test
    fun twoKnightsCanStillDeliverCheckmate() {
        // Ka8 vs Kb6, Nc6 (covers b8, a7) and Ne8-c7 giving mate: K+N+N is not insufficient material.
        val before = ClassicState.fromFen("k3N3/8/1KN5/8/8/8/8/8 w - - 0 1")
        assertEquals(GameStatus.Ongoing, ClassicVariant.status(before))
        val after = ClassicVariant.apply(before, normal("e8c7"))
        assertEquals(GameStatus.Finished(Color.WHITE, EndReason.CHECKMATE), ClassicVariant.status(after))
    }

    @Test
    fun doubleCheckOnlyKingMoves() {
        // White Ke1 attacked by rook e8 and bishop a5; blocking/capturing one does not help.
        val b = board("4r2k/8/8/b7/8/8/2P5/3QK3 w - - 0 1")
        val moves = ClassicRules.legalMoves(b)
        assertTrue(moves.all { it.from == sq("e1") }, "only king moves in double check: $moves")
        assertEquals(setOf("f1", "f2"), moves.map { it.to.name }.toSet())
    }

    @Test
    fun stalemateIsADraw() {
        val state = ClassicState.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertEquals(GameStatus.Finished(null, EndReason.STALEMATE), ClassicVariant.status(state))
        // Black Ka8: a7 holds a white pawn defended by Kb6, b7 is covered by Kb6 and b8 by the pawn. Not in check.
        val pawnAndKing = ClassicState.fromFen("k7/P7/1K6/8/8/8/8/8 b - - 0 1")
        assertFalse(ClassicRules.isInCheck(pawnAndKing.board, Color.BLACK))
        assertEquals(GameStatus.Finished(null, EndReason.STALEMATE), ClassicVariant.status(pawnAndKing))
    }

    @Test
    fun insufficientMaterial() {
        fun insufficient(fen: String) = ClassicRules.isInsufficientMaterial(board(fen))
        assertTrue(insufficient("4k3/8/8/8/8/8/8/4K3 w - - 0 1"), "bare kings")
        assertTrue(insufficient("4k3/8/8/8/8/8/8/4KB2 w - - 0 1"), "K+B vs K")
        assertTrue(insufficient("4k3/8/8/8/8/8/8/4KN2 w - - 0 1"), "K+N vs K")
        assertTrue(insufficient("2b1k3/8/8/8/8/8/8/4KB2 w - - 0 1"), "K+B vs K+B same colour (c8 and f1 are both light)")
        assertTrue(insufficient("4k3/8/8/8/8/4B3/8/2B1K3 w - - 0 1"), "two bishops on the same colour")
        assertFalse(insufficient("2b1k3/8/8/8/8/8/8/2B1K3 w - - 0 1"), "K+B vs K+B opposite colours (c8 light, c1 dark)")
        assertFalse(insufficient("4k3/8/8/8/8/8/8/2BBK3 w - - 0 1"), "two bishops on different colours")
        assertFalse(insufficient("4k3/8/8/8/8/8/8/4KNN1 w - - 0 1"), "K+N+N vs K can still be mated")
        assertFalse(insufficient("4k2n/8/8/8/8/8/8/2B1K3 w - - 0 1"), "K+B vs K+N")
        assertFalse(insufficient("4k1n1/8/8/8/8/8/8/4KN2 w - - 0 1"), "K+N vs K+N")
        assertFalse(insufficient("4k3/8/8/8/8/8/8/2BNK3 w - - 0 1"), "K+B+N")
        assertFalse(insufficient("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1"), "pawn")
        assertFalse(insufficient("4k3/8/8/8/8/8/8/4K2R w - - 0 1"), "rook")
        assertFalse(insufficient("4k3/8/8/8/8/8/8/4KQ2 w - - 0 1"), "queen")
        assertEquals(
            GameStatus.Finished(null, EndReason.INSUFFICIENT_MATERIAL),
            ClassicVariant.status(ClassicState.fromFen("4k3/8/8/8/8/8/8/4KB2 w - - 0 1")),
        )
    }

    @Test
    fun insufficientMaterialDetectedRightAfterTheCapture() {
        val after = apply("4k3/8/8/8/8/8/6r1/4KB2 w - - 0 1", "f1g2")
        assertEquals(GameStatus.Finished(null, EndReason.INSUFFICIENT_MATERIAL), ClassicVariant.status(after))
    }

    @Test
    fun fiftyMoveRule() {
        val state = ClassicState.fromFen("4k3/8/8/8/8/8/8/4K2R w - - 99 60")
        val after = ClassicVariant.apply(state, normal("h1h2"))
        assertEquals(100, after.halfmoveClock)
        assertEquals(GameStatus.Finished(null, EndReason.FIFTY_MOVE_RULE), ClassicVariant.status(after))
        val reset = ClassicVariant.apply(ClassicState.fromFen("4k3/8/8/8/8/8/P7/4K2R w - - 99 60"), normal("a2a3"))
        assertEquals(0, reset.halfmoveClock)
    }

    @Test
    fun fiftyMoveClockResetByPromotionAndByCapture() {
        val promoted = apply("4k3/P7/8/8/8/8/8/4K3 w - - 77 60", "a7a8q")
        assertEquals(0, promoted.halfmoveClock)
        val captured = apply("4k3/8/8/8/8/8/3r4/4K3 w - - 77 60", "e1d2")
        assertEquals(0, captured.halfmoveClock)
        val quiet = apply("4k3/8/8/8/8/8/8/4K2R w - - 77 60", "h1h2")
        assertEquals(78, quiet.halfmoveClock)
    }

    @Test
    fun checkmateOnTheHundredthPlyBeatsTheFiftyMoveRule() {
        val mated = apply("7k/6pp/8/8/8/8/8/R5K1 w - - 99 80", "a1a8")
        assertEquals(100, mated.halfmoveClock)
        assertEquals(GameStatus.Finished(Color.WHITE, EndReason.CHECKMATE), ClassicVariant.status(mated))
        // The same clock without the mate is a draw even though mate in one is still available.
        val quiet = apply("7k/6pp/8/8/8/8/8/R5K1 w - - 99 80", "g1g2")
        assertEquals(GameStatus.Finished(null, EndReason.FIFTY_MOVE_RULE), ClassicVariant.status(quiet))
    }

    @Test
    fun threefoldRepetition() {
        val game = Game(GameKind.CLASSIC)
        val shuffle = arrayOf("g1f3", "g8f6", "f3g1", "f6g8")
        game.play(*shuffle)
        assertEquals(GameStatus.Ongoing, game.status())
        game.play(*shuffle)
        assertEquals(GameStatus.Finished(null, EndReason.THREEFOLD_REPETITION), game.status())
    }

    @Test
    fun threefoldRepetitionDistinguishesCastlingRights() {
        val game = Game(GameKind.CLASSIC).play("g1f3", "g8f6", "h1g1", "h8g8", "g1h1", "g8h8", "f3g1", "f6g8")
        // Same placement as the initial position but king side rights are gone: first occurrence.
        assertEquals(GameStatus.Ongoing, game.status())
        game.play("g1f3", "g8f6", "f3g1", "f6g8")
        assertEquals(GameStatus.Ongoing, game.status(), "second occurrence; the initial position must not count")
        // Knights on f3/f6 with rights Qq is the first position to occur three times (plies 6, 11 and 15).
        game.play("g1f3")
        assertEquals(GameStatus.Ongoing, game.status())
        game.play("g8f6")
        assertEquals(GameStatus.Finished(null, EndReason.THREEFOLD_REPETITION), game.status())
    }

    @Test
    fun threefoldRepetitionDistinguishesUsableEnPassant() {
        val game = Game(GameKind.CLASSIC).play("e2e4", "c7c5", "e4e5", "d7d5")
        assertEquals(sq("d6"), (game.state as ClassicState).board.enPassant)
        game.play("g1f3", "g8f6", "f3g1", "f6g8")
        game.play("g1f3", "g8f6", "f3g1", "f6g8")
        assertEquals(GameStatus.Ongoing, game.status(), "the position with en passant available is a different position")
        // The position after g1f3 (black to move) is the first to occur three times (plies 5, 9 and 13).
        game.play("g1f3")
        assertEquals(GameStatus.Finished(null, EndReason.THREEFOLD_REPETITION), game.status())
    }

    @Test
    fun fullmoveNumberAdvancesAfterBlack() {
        val game = Game(GameKind.CLASSIC).play("e2e4", "e7e5")
        assertEquals(2, (game.state as ClassicState).fullmoveNumber)
        assertEquals("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2", (game.state as ClassicState).fen)
        val afterBlack = apply("4k3/8/8/8/8/8/8/4K3 b - - 0 7", "e8d8")
        assertEquals(8, afterBlack.fullmoveNumber)
        val afterWhite = ClassicVariant.apply(afterBlack, normal("e1d1"))
        assertEquals(8, afterWhite.fullmoveNumber)
        assertEquals("3k4/8/8/8/8/8/8/3K4 b - - 2 8", afterWhite.fen)
    }

    @Test
    fun viewReportsCheck() {
        val game = Game(GameKind.CLASSIC).play("e2e4", "f7f5", "d1h5")
        val view = game.view()
        assertEquals(1.0, view.checkProbability)
        assertEquals(Color.BLACK, view.sideToMove)
        assertEquals(listOf(CellEntry(piece('Q'), 1.0)), view.cell(sq("h5")).entries)
        assertTrue(view.cell(sq("e2")).isEmpty)
    }

    @Test
    fun replayRejectsIllegalHistory() {
        val moves = listOf(normal("e2e4"), normal("e7e5"), normal("e4e5"))
        var failed = false
        try {
            Game.replay(GameKind.CLASSIC, moves)
        } catch (e: IllegalMoveException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun classicRejectsQuantumMoves() {
        val game = Game(GameKind.CLASSIC)
        assertFalse(game.isLegal(split("e2", "e3", "e4")))
        assertFalse(game.canObserve(sq("e2")))
    }
}
