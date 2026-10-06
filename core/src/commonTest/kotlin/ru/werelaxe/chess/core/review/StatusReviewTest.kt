package ru.werelaxe.chess.core.review

import ru.werelaxe.chess.core.ClassicRules
import ru.werelaxe.chess.core.ClassicState
import ru.werelaxe.chess.core.ClassicVariant
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameStatus
import ru.werelaxe.chess.core.IllegalMoveException
import ru.werelaxe.chess.core.board
import ru.werelaxe.chess.core.normal
import ru.werelaxe.chess.core.play
import ru.werelaxe.chess.core.sq
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Review probes for status evaluation and draw-rule bookkeeping. */
class StatusReviewTest {
    private fun apply(fen: String, vararg ucis: String): ClassicState {
        var state = ClassicState.fromFen(fen)
        for (uci in ucis) state = ClassicVariant.apply(state, normal(uci))
        return state
    }

    @Test
    fun checkmateOnHundredthPlyIsCheckmate() {
        val after = apply("7k/6pp/8/8/8/8/8/R5K1 w - - 99 80", "a1a8")
        assertEquals(100, after.halfmoveClock)
        assertEquals(GameStatus.Finished(Color.WHITE, EndReason.CHECKMATE), ClassicVariant.status(after))
    }

    @Test
    fun fiftyMoveDrawEvenWithMateInOneAvailable() {
        val after = apply("7k/6pp/8/8/8/8/8/R5K1 w - - 99 80", "g1g2")
        assertEquals(GameStatus.Finished(null, EndReason.FIFTY_MOVE_RULE), ClassicVariant.status(after))
    }

    @Test
    fun fiftyMoveClockResetByPromotionAndByKingCapture() {
        val promoted = apply("4k3/P7/8/8/8/8/8/4K3 w - - 77 60", "a7a8q")
        assertEquals(0, promoted.halfmoveClock)
        val captured = apply("4k3/8/8/8/8/8/3r4/4K3 w - - 77 60", "e1d2")
        assertEquals(0, captured.halfmoveClock)
        val quiet = apply("4k3/8/8/8/8/8/8/4K2R w - - 77 60", "h1h2")
        assertEquals(78, quiet.halfmoveClock)
    }

    @Test
    fun insufficientMaterialEdgeCases() {
        fun insufficient(fen: String) = ClassicRules.isInsufficientMaterial(board(fen))
        assertFalse(insufficient("4k3/8/8/8/8/8/8/4KNN1 w - - 0 1"), "K+N+N vs K can still be mated")
        assertTrue(insufficient("4k3/8/8/8/8/4B3/8/2B1K3 w - - 0 1"), "two bishops on the same colour")
        assertFalse(insufficient("4k3/8/8/8/8/8/8/2BBK3 w - - 0 1"), "two bishops on different colours")
        assertFalse(insufficient("4k2n/8/8/8/8/8/8/2B1K3 w - - 0 1"), "K+B vs K+N")
        assertFalse(insufficient("4k1n1/8/8/8/8/8/8/4KN2 w - - 0 1"), "K+N vs K+N")
        assertFalse(insufficient("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1"), "pawn")
        assertFalse(insufficient("4k3/8/8/8/8/8/8/4K2R w - - 0 1"), "rook")
        assertFalse(insufficient("4k3/8/8/8/8/8/8/2BNK3 w - - 0 1"), "K+B+N")
        assertTrue(insufficient("4k3/8/8/8/8/8/8/4K3 w - - 0 1"), "bare kings")
        assertTrue(insufficient("2b1k3/8/8/8/8/8/8/4KB2 w - - 0 1"), "K+B vs K+B same colour (c8 and f1 are both light)")
        assertFalse(insufficient("2b1k3/8/8/8/8/8/8/2B1K3 w - - 0 1"), "K+B vs K+B opposite colours (c8 light, c1 dark)")
    }

    @Test
    fun insufficientMaterialDetectedRightAfterTheCapture() {
        val after = apply("4k3/8/8/8/8/8/6r1/4KB2 w - - 0 1", "f1g2")
        assertEquals(GameStatus.Finished(null, EndReason.INSUFFICIENT_MATERIAL), ClassicVariant.status(after))
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
    fun threefoldRepetitionCountsTheInitialPosition() {
        // Initial position: 1. After 1.Nf3 Nf6 2.Ng1 Ng8: 2. One more cycle: 3.
        val game = Game(GameKind.CLASSIC).play("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1")
        assertEquals(GameStatus.Ongoing, game.status())
        game.play("f6g8")
        assertEquals(GameStatus.Finished(null, EndReason.THREEFOLD_REPETITION), game.status())
    }

    @Test
    fun fullmoveNumberFromBlackToMoveFen() {
        val state = ClassicState.fromFen("4k3/8/8/8/8/8/8/4K3 b - - 0 7")
        val afterBlack = ClassicVariant.apply(state, normal("e8d8"))
        assertEquals(8, afterBlack.fullmoveNumber)
        val afterWhite = ClassicVariant.apply(afterBlack, normal("e1d1"))
        assertEquals(8, afterWhite.fullmoveNumber)
        assertEquals("3k4/8/8/8/8/8/8/3K4 b - - 2 8", afterWhite.fen)
    }

    @Test
    fun stalemateByPawnAndKing() {
        // Black Ka8: a7 holds a white pawn defended by Kb6, b7 is covered by Kb6 and b8 by the pawn. Not in check.
        val state = ClassicState.fromFen("k7/P7/1K6/8/8/8/8/8 b - - 0 1")
        assertFalse(ClassicRules.isInCheck(state.board, Color.BLACK))
        assertEquals(GameStatus.Finished(null, EndReason.STALEMATE), ClassicVariant.status(state))
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
    fun gameOverRejectsFurtherMoves() {
        val game = Game(GameKind.CLASSIC).play("f2f3", "e7e5", "g2g4", "d8h4")
        assertTrue(game.status().isOver)
        assertFalse(game.isLegal(normal("a2a3")))
        assertFailsWith<IllegalMoveException> { game.apply(normal("a2a3")) }
    }
}
