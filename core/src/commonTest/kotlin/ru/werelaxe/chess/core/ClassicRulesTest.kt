package ru.werelaxe.chess.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClassicRulesTest {
    private fun targets(fen: String, from: String): Set<String> =
        ClassicRules.legalMoves(board(fen), sq(from)).map { it.to.name }.toSet()

    @Test
    fun enPassantCaptureEscapesCheckFromDoublePushedPawn() {
        val game = Game(GameKind.CLASSIC)
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
        assertEquals(GameStatus.Ongoing, game.status())
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
    fun enPassantOnlyRightAfterTheDoublePush() {
        val game = Game(GameKind.CLASSIC).play("e2e4", "a7a6", "e4e5", "d7d5")
        assertTrue(game.isLegal(normal("e5d6")))
        game.play("h2h3", "h7h6")
        assertFalse(game.isLegal(normal("e5d6")))
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
    fun castlingThroughAttackedSquareIsIllegal() {
        assertFalse("g1" in targets("5r1k/8/8/8/8/8/8/R3K2R w KQ - 0 1", "e1"))
        assertTrue("c1" in targets("5r1k/8/8/8/8/8/8/R3K2R w KQ - 0 1", "e1"))
    }

    @Test
    fun queenSideCastlingAllowedWhenOnlyB1IsAttacked() {
        assertTrue("c1" in targets("1r5k/8/8/8/8/8/8/R3K2R w KQ - 0 1", "e1"))
        assertFalse("c1" in targets("3r3k/8/8/8/8/8/8/R3K2R w KQ - 0 1", "e1"))
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
    fun castlingRightsLostAfterKingOrRookMoves() {
        val game = Game(GameKind.CLASSIC).play("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6")
        assertTrue(game.isLegal(normal("e1g1")))
        game.play("h1g1", "a7a6", "g1h1", "a6a5")
        assertFalse(game.isLegal(normal("e1g1")))
    }

    @Test
    fun castlingRightLostWhenRookIsCaptured() {
        val board = board("4k3/8/8/8/8/8/6b1/R3K2R b KQ - 0 1")
        val after = ClassicRules.apply(board, mv("g2h1"))
        assertFalse(after.castling.whiteKingSide)
        assertTrue(after.castling.whiteQueenSide)
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
    fun stalemateIsADraw() {
        val state = ClassicState.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertEquals(GameStatus.Finished(null, EndReason.STALEMATE), ClassicVariant.status(state))
    }

    @Test
    fun insufficientMaterial() {
        assertTrue(ClassicRules.isInsufficientMaterial(board("4k3/8/8/8/8/8/8/4K3 w - - 0 1")))
        assertTrue(ClassicRules.isInsufficientMaterial(board("4k3/8/8/8/8/8/8/4KB2 w - - 0 1")))
        assertTrue(ClassicRules.isInsufficientMaterial(board("4k3/8/8/8/8/8/8/4KN2 w - - 0 1")))
        assertTrue(ClassicRules.isInsufficientMaterial(board("4k3/5b2/8/8/8/8/8/4KB2 w - - 0 1")))
        assertFalse(ClassicRules.isInsufficientMaterial(board("4kb2/8/8/8/8/8/8/4KB2 w - - 0 1")))
        assertFalse(ClassicRules.isInsufficientMaterial(board("4k3/8/8/8/8/8/8/4KNN1 w - - 0 1")))
        assertFalse(ClassicRules.isInsufficientMaterial(board("4k3/8/8/8/8/8/8/4KQ2 w - - 0 1")))
        assertEquals(
            GameStatus.Finished(null, EndReason.INSUFFICIENT_MATERIAL),
            ClassicVariant.status(ClassicState.fromFen("4k3/8/8/8/8/8/8/4KB2 w - - 0 1")),
        )
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
    fun threefoldRepetition() {
        val game = Game(GameKind.CLASSIC)
        val shuffle = arrayOf("g1f3", "g8f6", "f3g1", "f6g8")
        game.play(*shuffle)
        assertEquals(GameStatus.Ongoing, game.status())
        game.play(*shuffle)
        assertEquals(GameStatus.Finished(null, EndReason.THREEFOLD_REPETITION), game.status())
    }

    @Test
    fun fullmoveNumberAdvancesAfterBlack() {
        val game = Game(GameKind.CLASSIC).play("e2e4", "e7e5")
        assertEquals(2, (game.state as ClassicState).fullmoveNumber)
        assertEquals("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2", (game.state as ClassicState).fen)
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
