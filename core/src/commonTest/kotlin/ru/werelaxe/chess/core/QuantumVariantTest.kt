package ru.werelaxe.chess.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuantumVariantTest {
    private fun quantum(fen: String): QuantumState = QuantumState.of(board(fen))

    private fun QuantumState.prob(square: String, piece: Piece?): Double = distribution(sq(square))[piece] ?: 0.0

    private fun QuantumState.boards(): List<Board> = universes.keys.toList()

    @Test
    fun splitCreatesTwoEqualUniverses() {
        val game = Game(GameKind.QUANTUM)
        assertEquals(setOf(sq("e3"), sq("e4")), game.legalTargets(sq("e2")))
        assertEquals(setOf(sq("e2"), sq("e4")), game.splitSecondTargets(sq("e2"), sq("e3")))
        game.apply(split("e2", "e3", "e4"))
        val state = game.state as QuantumState
        assertEquals(2, state.universeCount)
        assertEquals(2L, state.totalWeight)
        assertEquals(Color.BLACK, state.sideToMove)
        assertEquals(1.0, state.prob("e2", null))
        assertEquals(0.5, state.prob("e3", piece('P')))
        assertEquals(0.5, state.prob("e4", piece('P')))
        assertEquals(0.5, state.prob("e4", null))
        val cell = game.view().cell(sq("e4"))
        assertEquals(listOf(CellEntry(piece('P'), 0.5), CellEntry(null, 0.5)), cell.entries)
    }

    @Test
    fun splitWithStayLeavesHalfInPlace() {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("e2", "e4", "e2"))
        val state = game.state as QuantumState
        assertEquals(0.5, state.prob("e2", piece('P')))
        assertEquals(0.5, state.prob("e4", piece('P')))
    }

    @Test
    fun illegalSplits() {
        val game = Game(GameKind.QUANTUM)
        assertFalse(game.isLegal(split("e2", "e2", "e4")))
        assertFalse(game.isLegal(split("e2", "e4", "e4")))
        assertFalse(game.isLegal(split("e2", "e5", "e4")))
        assertFalse(game.isLegal(split("e2", "e4", "e5")))
        assertFalse(game.isLegal(split("e7", "e6", "e5")))
        assertFailsWith<IllegalMoveException> { game.apply(split("e2", "e5", "e4")) }
    }

    @Test
    fun normalMoveAppliesOnlyWhereLegal() {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("e2", "e3", "e4"))
        game.apply(normal("a7a6"))
        assertTrue(game.isLegal(normal("e4e5")))
        game.apply(normal("e4e5"))
        val state = game.state as QuantumState
        assertEquals(2, state.universeCount)
        assertEquals(0.5, state.prob("e5", piece('P')))
        assertEquals(0.5, state.prob("e3", piece('P')))
        assertEquals(0.0, state.prob("e4", piece('P')))
        assertEquals(Color.BLACK, state.sideToMove)
    }

    @Test
    fun universesMergeWhenTheyBecomeIdentical() {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("e2", "e4", "e2"))
        game.apply(normal("a7a6"))
        game.apply(normal("e2e4"))
        val state = game.state as QuantumState
        assertEquals(1, state.universeCount)
        assertEquals(1L, state.totalWeight)
        assertEquals(1.0, state.prob("e4", piece('P')))
    }

    @Test
    fun splitIllegalInSomeUniversesDoublesTheirWeight() {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("g1", "f3", "h3"))
        game.apply(normal("a7a6"))
        // The knight on f3 exists in one universe only; splitting it leaves the other universe untouched.
        game.apply(split("f3", "e5", "g5"))
        val state = game.state as QuantumState
        assertEquals(3, state.universeCount)
        assertEquals(4L, state.totalWeight)
        assertEquals(0.5, state.prob("h3", piece('N')))
        assertEquals(0.25, state.prob("e5", piece('N')))
        assertEquals(0.25, state.prob("g5", piece('N')))
    }

    @Test
    fun observationCollapsesUniverses() {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("e2", "e3", "e4"))
        game.apply(normal("a7a6"))
        assertTrue(game.canObserve(sq("e4")))
        assertFalse(game.canObserve(sq("e2")))
        assertFalse(game.canObserve(sq("a6")))
        assertFalse(game.isLegal(GameMove.Observe(sq("e4"))))
        assertFalse(game.isLegal(observe("e4", piece('N'))))
        assertFailsWith<IllegalMoveException> { game.apply(GameMove.Observe(sq("e4"))) }

        val resolved = game.resolve(GameMove.Observe(sq("e4")), Random(7)) as GameMove.Observe
        assertTrue(resolved.outcome != null)
        game.apply(observe("e4", piece('P')))
        val state = game.state as QuantumState
        assertEquals(1, state.universeCount)
        assertEquals(1.0, state.prob("e4", piece('P')))
        assertEquals(Color.BLACK, state.sideToMove)
    }

    @Test
    fun observingEmptyOutcomeKeepsTheOtherBranch() {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("e2", "e3", "e4"))
        game.apply(normal("a7a6"))
        game.apply(observe("e4", null))
        val state = game.state as QuantumState
        assertEquals(1, state.universeCount)
        assertEquals(1.0, state.prob("e3", piece('P')))
    }

    @Test
    fun samplingFollowsTheDistribution() {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("g1", "f3", "h3"))
        game.apply(normal("a7a6"))
        game.apply(split("f3", "e5", "g5"))
        game.apply(normal("a6a5"))
        val state = game.state as QuantumState
        val random = Random(42)
        var knights = 0
        repeat(2000) { if (QuantumVariant.sampleContent(state, sq("h3"), random) != null) knights++ }
        assertTrue(knights in 900..1100, "expected about half of the samples to see the knight, got $knights")
    }

    @Test
    fun universesWhereTheMoveDoesNotApplyAreLeftIntact() {
        // White Pa5 Pd5 Kh1, Black Pb7 Pe7 Kh8. Black splits b7 to b5 or stays.
        val start = quantum("7k/1p2p3/8/P2P4/8/8/8/7K b - - 0 1")
        val afterSplit = QuantumVariant.apply(start, split("b7", "b5", "b7"))
        val pushed = afterSplit.boards().first { it[sq("b5")] != null }
        val stayed = afterSplit.boards().first { it[sq("b7")] != null }
        assertEquals(sq("b6"), pushed.enPassant)
        assertNull(stayed.enPassant)

        // a5xb6 is an en passant capture in one universe and impossible in the other.
        val captured = QuantumVariant.apply(afterSplit, normal("a5b6"))
        val boards = captured.boards()
        assertEquals(2, boards.size)
        val withCapture = boards.first { it[sq("b6")] != null }
        assertEquals(piece('P'), withCapture[sq("b6")])
        assertNull(withCapture[sq("b5")])
        assertNull(withCapture[sq("a5")])
        val untouched = boards.first { it[sq("b6")] == null }
        assertEquals(piece('P'), untouched[sq("a5")])
        assertEquals(piece('p'), untouched[sq("b7")])
        assertEquals(Color.BLACK, untouched.sideToMove)
    }

    @Test
    fun castlingAppliesOnlyWhereItIsLegal() {
        // Black bishop c4 attacks f1. Black splits the bishop to d5 or keeps it on c4.
        val start = quantum("4k3/8/8/8/2b5/8/8/4K2R b K - 0 1")
        val afterSplit = QuantumVariant.apply(start, split("c4", "d5", "c4"))
        assertTrue(QuantumVariant.isLegal(afterSplit, normal("e1g1")))
        val castled = QuantumVariant.apply(afterSplit, normal("e1g1"))
        val boards = castled.boards()
        assertEquals(2, boards.size)
        val applied = boards.first { it[sq("g1")] != null }
        assertEquals(piece('K'), applied[sq("g1")])
        assertEquals(piece('R'), applied[sq("f1")])
        assertNull(applied[sq("h1")])
        val skipped = boards.first { it[sq("g1")] == null }
        assertEquals(piece('K'), skipped[sq("e1")])
        assertEquals(piece('R'), skipped[sq("h1")])
        assertTrue(skipped.castling.whiteKingSide)
    }

    @Test
    fun kingCanBeCapturedInUniversesWhereCheckWasIgnored() {
        // White Ke1 Rh1, Black Ke8. The rook goes to h8 (check) in one universe and stays in the other.
        val start = quantum("4k3/8/8/8/8/8/8/4K2R w - - 0 1")
        val afterSplit = QuantumVariant.apply(start, split("h1", "h8", "h1"))
        // Kd8 is legal only where the rook stayed; the checked universe passes.
        val moved = QuantumVariant.apply(afterSplit, normal("e8d8"))
        assertEquals(0.5, moved.prob("d8", piece('k')))
        assertEquals(0.5, moved.prob("e8", piece('k')))
        assertEquals(GameStatus.Ongoing, QuantumVariant.status(moved))

        // The rook on h8 captures the king that stayed on e8.
        assertTrue(QuantumVariant.isLegal(moved, normal("h8e8")))
        val captured = QuantumVariant.apply(moved, normal("h8e8"))
        assertEquals(0.5, captured.prob("e8", piece('R')))
        assertEquals(0.5, captured.prob("d8", piece('k')))
        assertTrue(captured.hasKingAnywhere(Color.BLACK))
        assertEquals(GameStatus.Ongoing, QuantumVariant.status(captured))
        assertEquals(0.0, QuantumVariant.view(captured).checkProbability)
    }

    @Test
    fun losingTheLastKingEndsTheGame() {
        val state = quantum("R3k3/8/8/8/8/8/8/4K3 w - - 0 1")
        val captured = QuantumVariant.apply(state, normal("a8e8"))
        assertEquals(GameStatus.Finished(Color.WHITE, EndReason.KING_CAPTURED), QuantumVariant.status(captured))
    }

    @Test
    fun checkmateAndStalemateInASingleUniverse() {
        val game = Game(GameKind.QUANTUM).play("f2f3", "e7e5", "g2g4", "d8h4")
        assertEquals(GameStatus.Finished(Color.BLACK, EndReason.CHECKMATE), game.status())
        assertFalse(game.isLegal(normal("a2a3")))
        val stalemate = quantum("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertEquals(GameStatus.Finished(null, EndReason.STALEMATE), QuantumVariant.status(stalemate))
    }

    @Test
    fun checkProbabilityIsWeighted() {
        val start = quantum("4k3/8/8/8/8/8/8/4K2R w - - 0 1")
        val afterSplit = QuantumVariant.apply(start, split("h1", "h8", "h1"))
        assertEquals(0.5, QuantumVariant.view(afterSplit).checkProbability)
    }

    @Test
    fun promotionInsideASplit() {
        val start = quantum("4k3/P7/8/8/8/8/8/4K3 w - - 0 1")
        assertTrue(QuantumVariant.requiresPromotion(start, sq("a7"), sq("a8")))
        assertFalse(QuantumVariant.isLegal(start, split("a7", "a8", "a7")))
        val promoted = QuantumVariant.apply(start, split("a7", "a8", "a7", PieceType.KNIGHT))
        assertEquals(0.5, promoted.prob("a8", piece('N')))
        assertEquals(0.5, promoted.prob("a7", piece('P')))
    }

    @Test
    fun promotionPieceIgnoredForNonPawns() {
        // The a7 square holds a pawn in one universe and a rook in the other.
        val start = quantum("4k3/8/R7/8/8/8/8/4K3 w - - 0 1")
        val rookSplit = QuantumVariant.apply(start, split("a6", "a7", "a6"))
        val quiet = QuantumVariant.apply(rookSplit, normal("e8d8"))
        assertEquals(0.5, quiet.prob("a7", piece('R')))
        val moved = QuantumVariant.apply(quiet, GameMove.Normal(sq("a7"), sq("a8"), PieceType.QUEEN))
        assertEquals(0.5, moved.prob("a8", piece('R')))
    }

    @Test
    fun threefoldRepetition() {
        val game = Game(GameKind.QUANTUM)
        val shuffle = arrayOf("g1f3", "g8f6", "f3g1", "f6g8")
        game.play(*shuffle)
        assertEquals(GameStatus.Ongoing, game.status())
        game.play(*shuffle)
        assertEquals(GameStatus.Finished(null, EndReason.THREEFOLD_REPETITION), game.status())
    }

    @Test
    fun fiftyMoveClockResetsOnlyWhenSomethingIrreversibleHappensSomewhere() {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("g1", "f3", "h3"))
        assertEquals(1, (game.state as QuantumState).halfmoveClock)
        game.apply(normal("e7e5"))
        assertEquals(0, (game.state as QuantumState).halfmoveClock)
        game.apply(normal("h3g5"))
        assertEquals(1, (game.state as QuantumState).halfmoveClock)
    }

    @Test
    fun moveSerializationRoundTrip() {
        val moves = listOf(
            normal("e2e4"),
            normal("e7e8q"),
            split("g1", "f3", "h3"),
            split("a7", "a8", "a7", PieceType.QUEEN),
            GameMove.Observe(sq("e4")),
            observe("e4", piece('P')),
            observe("e4", null),
        )
        val json = ChessJson.encodeMoves(moves)
        assertEquals(moves, ChessJson.decodeMoves(json))
        assertEquals("""{"type":"normal","from":"e2","to":"e4"}""", ChessJson.encodeMove(normal("e2e4")))
        assertEquals("""{"type":"observe","square":"e4","outcome":{"piece":null}}""", ChessJson.encodeMove(observe("e4", null)))
        assertEquals("""{"type":"observe","square":"e4"}""", ChessJson.encodeMove(GameMove.Observe(sq("e4"))))
    }

    @Test
    fun statusSerialization() {
        assertEquals("""{"type":"ongoing"}""", ChessJson.encodeStatus(GameStatus.Ongoing))
        assertEquals(
            """{"type":"finished","winner":"WHITE","reason":"CHECKMATE"}""",
            ChessJson.encodeStatus(GameStatus.Finished(Color.WHITE, EndReason.CHECKMATE)),
        )
        assertEquals(
            """{"type":"finished","winner":null,"reason":"STALEMATE"}""",
            ChessJson.encodeStatus(GameStatus.Finished(null, EndReason.STALEMATE)),
        )
    }

    @Test
    fun replayReproducesTheSameDistribution() {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("e2", "e3", "e4"))
        game.apply(normal("a7a6"))
        game.apply(split("g1", "f3", "h3"))
        game.apply(normal("a6a5"))
        val replayed = Game.replay(GameKind.QUANTUM, ChessJson.decodeMoves(ChessJson.encodeMoves(game.history)))
        assertEquals((game.state as QuantumState).universes, (replayed.state as QuantumState).universes)
    }
}
