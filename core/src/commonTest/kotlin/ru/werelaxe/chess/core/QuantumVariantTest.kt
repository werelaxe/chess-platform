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

    /** A state with one equally weighted universe per FEN. */
    private fun universes(vararg fens: String): QuantumState {
        val universes = fens.associate { board(it) to 1L }
        return QuantumState(universes, 0, mapOf(universes to 1), null)
    }

    /** The e2 pawn stands on e3 or e4 with equal probability; black has replied a7a6. */
    private fun superposedPawn(): Game {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("e2", "e3", "e4"))
        game.apply(normal("a7a6"))
        return game
    }

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
    fun legalTargetsAndSplitTargetsAgreeWithIsLegal() {
        val game = superposedPawn()
        game.apply(split("g1", "f3", "h3"))
        game.apply(normal("d7d5"))
        for (from in Square.ALL) {
            val targets = game.legalTargets(from)
            for (to in Square.ALL) {
                val promotion = if (game.requiresPromotion(from, to)) PieceType.QUEEN else null
                assertEquals(to in targets, game.isLegal(GameMove.Normal(from, to, promotion)), "$from -> $to")
            }
            for (first in targets) {
                val seconds = game.splitSecondTargets(from, first)
                assertTrue(from in seconds, "stay must be offered for $from -> $first")
                for (second in seconds) assertTrue(game.isLegal(GameMove.Split(from, first, second)), "$from -> $first / $second")
                assertFalse(game.isLegal(GameMove.Split(from, first, first)))
            }
            assertTrue(game.splitSecondTargets(from, from).isEmpty())
        }
    }

    @Test
    fun normalMoveAppliesOnlyWhereLegal() {
        val game = superposedPawn()
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
    fun gcdNormalizationKeepsWeightsSmallWhenUniversesMerge() {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("g1", "f3", "h3"))
        game.apply(normal("a7a6"))
        game.apply(split("f3", "e5", "g5"))
        game.apply(normal("a6a5"))
        assertEquals(listOf(1L, 1L, 2L), (game.state as QuantumState).universes.values.sorted())
        game.apply(normal("e5f3"))
        game.apply(normal("a5a4"))
        // g5 -> f3 merges with the universe where the knight already returned to f3: weights 2 and 2 become 1 and 1.
        game.apply(normal("g5f3"))
        val merged = game.state as QuantumState
        assertEquals(2, merged.universeCount)
        assertEquals(listOf(1L, 1L), merged.universes.values.sorted())
        assertEquals(2L, merged.totalWeight)
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
    fun targetsLegalInDisjointUniversesEachGetDoubleWeight() {
        val b3Blocked = "4k3/8/8/8/3N4/1P6/8/4K3 w - - 0 1"
        val f3Blocked = "4k3/8/8/8/3N4/5P2/8/4K3 w - - 0 1"
        val start = universes(b3Blocked, f3Blocked)
        assertTrue(QuantumVariant.isLegal(start, split("d4", "b3", "f3")))
        val next = QuantumVariant.apply(start, split("d4", "b3", "f3"))
        assertEquals(2, next.universeCount)
        assertEquals(2L, next.totalWeight)
        assertEquals(0.5, next.prob("b3", piece('N')))
        assertEquals(0.5, next.prob("f3", piece('N')))
        assertEquals(0.0, next.prob("d4", piece('N')))
    }

    @Test
    fun observationCollapsesUniverses() {
        val game = superposedPawn()
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
        val game = superposedPawn()
        game.apply(observe("e4", null))
        val state = game.state as QuantumState
        assertEquals(1, state.universeCount)
        assertEquals(1.0, state.prob("e3", piece('P')))
    }

    @Test
    fun observeOnASquareWithTwoDifferentPiecesIsPossible() {
        // e4 holds a white pawn where e2-e4 was played and a black pawn where it was not (e7-e5-e4).
        val game = Game(GameKind.QUANTUM)
        game.apply(split("e2", "e4", "e2"))
        game.apply(normal("e7e5"))
        game.apply(normal("a2a3"))
        game.apply(normal("e5e4"))
        val state = game.state as QuantumState
        assertEquals(0.5, state.prob("e4", piece('P')))
        assertEquals(0.5, state.prob("e4", piece('p')))
        assertTrue(game.canObserve(sq("e4")))
        assertTrue(game.isLegal(observe("e4", piece('p'))))
        assertTrue(game.isLegal(observe("e4", piece('P'))))
        assertFalse(game.isLegal(observe("e4", null)))
    }

    @Test
    fun observationCountsAsAReversiblePly() {
        val start = quantum("4k3/8/8/8/8/8/8/N3K3 w - - 0 1")
        val afterSplit = QuantumVariant.apply(start, split("a1", "b3", "c2"))
        assertEquals(1, afterSplit.halfmoveClock)
        val observed = QuantumVariant.apply(afterSplit, observe("b3", null))
        assertEquals(2, observed.halfmoveClock)
        assertEquals(Color.WHITE, observed.sideToMove)
        assertEquals(1, observed.universeCount)
        assertEquals(1.0, observed.prob("c2", piece('N')))
    }

    @Test
    fun resolveFillsOnlyObservationsOfUncertainSquares() {
        val game = superposedPawn()
        val resolved = game.resolve(GameMove.Observe(sq("e4")), Random(3)) as GameMove.Observe
        assertTrue(resolved.outcome != null)
        assertTrue(game.isLegal(resolved))
        assertEquals(normal("g1f3"), game.resolve(normal("g1f3"), Random(3)))
        // A square with a certain content cannot be observed: resolve leaves the move unresolved and illegal.
        val certain = game.resolve(GameMove.Observe(sq("a6")), Random(3)) as GameMove.Observe
        assertNull(certain.outcome)
        assertFalse(game.isLegal(certain))
        assertFailsWith<IllegalMoveException> { game.apply(certain) }
    }

    @Test
    fun resolveSamplesTheOutcomeEvenWhenTheClientSuppliedOne() {
        // Game.resolve is where the authoritative side draws the observation outcome (ARCHITECTURE.md 1.2, 2.4).
        // A client may send an outcome of its choosing; resolve must replace it, otherwise the client decides the collapse.
        val game = superposedPawn()
        val state = game.state as QuantumState
        for (seed in 0 until 8) {
            val honest = QuantumVariant.sampleContent(state, sq("e4"), Random(seed))
            val forged = if (honest == null) piece('P') else null
            val resolved = game.resolve(observe("e4", forged), Random(seed)) as GameMove.Observe
            assertEquals(Observation(honest), resolved.outcome, "seed $seed: the authoritative side must not trust a client-chosen outcome")
        }
    }

    @Test
    fun forgedOutcomeCannotSteerTheServerPipeline() {
        // The server pipeline is resolve -> isLegal -> apply; a client that always proposes "pawn on e4"
        // must still see a fair collapse.
        var pawnSeen = 0
        for (seed in 0 until 40) {
            val game = superposedPawn()
            val resolved = game.resolve(observe("e4", piece('P')), Random(seed))
            assertTrue(game.isLegal(resolved))
            game.apply(resolved)
            if ((game.state as QuantumState).prob("e4", piece('P')) == 1.0) pawnSeen++
        }
        assertTrue(pawnSeen in 8..32, "a fair observation collapses to the pawn about half of the time, got $pawnSeen/40")
    }

    @Test
    fun samplingFollowsTheDistribution() {
        // Knight on h3 with probability 1/2, on e5 and g5 with probability 1/4 each.
        val game = Game(GameKind.QUANTUM)
        game.apply(split("g1", "f3", "h3"))
        game.apply(normal("a7a6"))
        game.apply(split("f3", "e5", "g5"))
        game.apply(normal("a6a5"))
        val state = game.state as QuantumState
        assertEquals(0.25, state.prob("e5", piece('N')))
        val random = Random(42)
        var onH3 = 0
        var onE5 = 0
        repeat(2000) {
            if (QuantumVariant.sampleContent(state, sq("h3"), random) != null) onH3++
            if (QuantumVariant.sampleContent(state, sq("e5"), random) != null) onE5++
        }
        assertTrue(onH3 in 900..1100, "expected about half of the samples to see the knight on h3, got $onH3")
        assertTrue(onE5 in 400..600, "expected about a quarter of the samples to see the knight on e5, got $onE5")
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
    fun passClearsEnPassantAndKeepsCastlingRights() {
        // White: Ke1 Rh1 (may castle) Pe5; Black: Ke8 Pd7. Black splits d7 to d5 or stays.
        val start = quantum("4k3/3p4/8/4P3/8/8/8/4K2R b K - 0 1")
        val afterSplit = QuantumVariant.apply(start, split("d7", "d5", "d7"))
        val pushed = afterSplit.boards().first { it[sq("d5")] != null }
        assertEquals(sq("d6"), pushed.enPassant)
        // e5xd6 is an en passant capture where the pawn was pushed and impossible where it stayed.
        val captured = QuantumVariant.apply(afterSplit, normal("e5d6"))
        val stayed = captured.boards().first { it[sq("d7")] != null }
        assertEquals(Color.BLACK, stayed.sideToMove)
        assertNull(stayed.enPassant)
        assertEquals(CastlingRights(whiteKingSide = true, whiteQueenSide = false, blackKingSide = false, blackQueenSide = false), stayed.castling)
        val applied = captured.boards().first { it[sq("d6")] != null }
        assertNull(applied[sq("d5")])
        assertEquals(piece('P'), applied[sq("d6")])
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

        // Observing e8 may collapse onto the universe where the king was captured: black loses.
        assertTrue(QuantumVariant.canObserve(captured, sq("e8")))
        val collapsed = QuantumVariant.apply(captured, observe("e8", piece('R')))
        assertFalse(collapsed.hasKingAnywhere(Color.BLACK))
        assertEquals(GameStatus.Finished(Color.WHITE, EndReason.KING_CAPTURED), QuantumVariant.status(collapsed))
    }

    @Test
    fun losingTheLastKingEndsTheGame() {
        val state = quantum("R3k3/8/8/8/8/8/8/4K3 w - - 0 1")
        val captured = QuantumVariant.apply(state, normal("a8e8"))
        assertEquals(GameStatus.Finished(Color.WHITE, EndReason.KING_CAPTURED), QuantumVariant.status(captured))
    }

    @Test
    fun capturingTheOpponentsLastKingWinsImmediately() {
        // Black also owns a pawn on a2, so in the universe where its king is already gone it still has moves.
        val start = quantum("4k3/8/8/8/8/8/p7/4K2R w - - 0 1")
        val afterSplit = QuantumVariant.apply(start, split("h1", "h8", "h1"))
        val moved = QuantumVariant.apply(afterSplit, normal("e8d8"))
        val captured = QuantumVariant.apply(moved, normal("h8e8"))
        val black = QuantumVariant.apply(captured, normal("d8c8"))
        val check = QuantumVariant.apply(black, normal("h1h8"))
        assertEquals(0.5, QuantumVariant.view(check).checkProbability)
        // Promoting is legal only in the kingless universe; where the king is in check the universe passes.
        val promoted = QuantumVariant.apply(check, normal("a2a1q"))
        assertEquals(GameStatus.Ongoing, QuantumVariant.status(promoted))
        assertTrue(promoted.hasKingAnywhere(Color.BLACK))
        val finished = QuantumVariant.apply(promoted, normal("h8c8"))
        assertFalse(finished.hasKingAnywhere(Color.BLACK))
        assertEquals(GameStatus.Finished(Color.WHITE, EndReason.KING_CAPTURED), QuantumVariant.status(finished))
    }

    @Test
    fun aKinglessSideKeepsPlayingWhereItHasPieces() {
        // Black has no king in one universe but still has a pawn move there; the other universe is a mate.
        val mated = "7k/6Q1/5K2/8/8/8/8/8 b - - 0 1"
        val kingless = "8/6Q1/5K2/8/8/8/p7/8 b - - 0 1"
        val mixed = universes(mated, kingless)
        assertEquals(GameStatus.Ongoing, QuantumVariant.status(mixed))
        assertTrue(QuantumVariant.isLegal(mixed, normal("a2a1q")))
        val moved = QuantumVariant.apply(mixed, normal("a2a1q"))
        assertEquals(Color.WHITE, moved.sideToMove)
        assertEquals(0, moved.halfmoveClock)
        assertEquals(0.5, moved.prob("a1", piece('q')))
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
    fun checkmateInEveryUniverseEndsTheGameAndBlocksObservations() {
        // Qh5 mates whether the e-pawn stands on e3 or e4.
        val game = Game(GameKind.QUANTUM)
        game.apply(split("e2", "e3", "e4"))
        game.apply(normal("f7f6"))
        game.apply(normal("a2a3"))
        game.apply(normal("g7g5"))
        game.apply(normal("d1h5"))
        assertEquals(GameStatus.Finished(Color.WHITE, EndReason.CHECKMATE), game.status())
        assertFalse(game.canObserve(sq("e4")))
        assertFalse(game.isLegal(observe("e4", piece('P'))))
        assertFalse(game.isLegal(split("a7", "a6", "a5")))
        assertFailsWith<IllegalMoveException> { game.apply(observe("e4", piece('P'))) }
        assertEquals(5, game.moveCount)
    }

    @Test
    fun checkmateInOneUniverseAndStalemateInAnotherIsCheckmate() {
        val mated = "7k/6Q1/5K2/8/8/8/8/8 b - - 0 1"
        val stalemated = "7k/5K2/6Q1/8/8/8/8/8 b - - 0 1"
        assertEquals(GameStatus.Finished(Color.WHITE, EndReason.CHECKMATE), QuantumVariant.status(universes(mated, stalemated)))
        assertEquals(GameStatus.Finished(Color.WHITE, EndReason.CHECKMATE), QuantumVariant.status(universes(stalemated, mated)))
    }

    @Test
    fun stalemateInOneUniverseAndNoPiecesInAnotherIsStalemate() {
        val stalemated = "7k/5K2/6Q1/8/8/8/8/8 b - - 0 1"
        val kingless = "8/5K2/6Q1/8/8/8/8/8 b - - 0 1"
        assertEquals(GameStatus.Finished(null, EndReason.STALEMATE), QuantumVariant.status(universes(stalemated, kingless)))
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
    fun promotionPieceIsSharedByBothSplitTargets() {
        val start = quantum("1n2k3/P7/8/8/8/8/8/4K3 w - - 0 1")
        assertTrue(QuantumVariant.requiresPromotion(start, sq("a7"), sq("b8")))
        val promoted = QuantumVariant.apply(start, split("a7", "a8", "b8", PieceType.ROOK))
        assertEquals(0.5, promoted.prob("a8", piece('R')))
        assertEquals(0.5, promoted.prob("b8", piece('R')))
        assertEquals(0.5, promoted.prob("b8", piece('n')))
        assertEquals(0, promoted.halfmoveClock)
    }

    @Test
    fun promotionToKingOrPawnIsRejected() {
        val fen = "4k3/P7/8/8/8/8/8/4K3 w - - 0 1"
        val start = quantum(fen)
        for (bogus in listOf(PieceType.KING, PieceType.PAWN)) {
            assertFalse(QuantumVariant.isLegal(start, GameMove.Normal(sq("a7"), sq("a8"), bogus)), "$bogus")
            assertFalse(QuantumVariant.isLegal(start, split("a7", "a8", "a7", bogus)), "$bogus")
            assertFailsWith<IllegalMoveException>("$bogus") { QuantumVariant.apply(start, GameMove.Normal(sq("a7"), sq("a8"), bogus)) }
            assertFailsWith<IllegalMoveException>("$bogus") { QuantumVariant.apply(start, split("a7", "a8", "a7", bogus)) }
            assertFalse(ClassicVariant.isLegal(ClassicState.fromFen(fen), GameMove.Normal(sq("a7"), sq("a8"), bogus)), "$bogus")
        }
        assertFalse(QuantumVariant.isLegal(start, normal("a7a8")))
        assertFalse(ClassicVariant.isLegal(ClassicState.fromFen(fen), normal("a7a8")))
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
    fun splitWithPromotionOnNonPawnIgnoresThePromotion() {
        val game = Game(GameKind.QUANTUM)
        assertTrue(game.isLegal(split("g1", "f3", "h3", PieceType.QUEEN)))
        game.apply(split("g1", "f3", "h3", PieceType.QUEEN))
        val state = game.state as QuantumState
        assertEquals(0.5, state.prob("f3", piece('N')))
        assertEquals(0.5, state.prob("h3", piece('N')))
        val queen = Piece(Color.WHITE, PieceType.QUEEN)
        assertTrue(state.boards().all { board -> board.squares.count { it == queen } == 1 }, "no queen may appear")
    }

    @Test
    fun threefoldRepetitionOfATwoUniverseDistribution() {
        val game = superposedPawn()
        val shuffle = arrayOf("g1f3", "g8f6", "f3g1", "f6g8")
        game.play(*shuffle)
        assertEquals(GameStatus.Ongoing, game.status())
        game.play(*shuffle)
        assertEquals(GameStatus.Finished(null, EndReason.THREEFOLD_REPETITION), game.status())
    }

    @Test
    fun threefoldRepetitionWithUnequalWeights() {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("g1", "f3", "g1"))
        game.apply(normal("g8f6"))
        game.apply(split("f3", "g1", "f3"))
        game.apply(normal("f6g8"))
        val state = game.state as QuantumState
        assertEquals(setOf(3L, 1L), state.universes.values.toSet())
        val shuffle = arrayOf("b1c3", "b8c6", "c3b1", "c6b8")
        game.play(*shuffle)
        assertEquals(GameStatus.Ongoing, game.status())
        game.play(*shuffle)
        assertEquals(GameStatus.Finished(null, EndReason.THREEFOLD_REPETITION), game.status())
    }

    @Test
    fun threefoldRepetitionWithLargeWeightsIsRecognized() {
        // Weights above 2^32 exercise the high word of Long hash codes on JS.
        val a = board("4k3/8/8/8/8/8/8/N3K3 w - - 0 1")
        val b = board("4k3/8/8/8/8/8/8/4K2N w - - 0 1")
        val universes = mapOf(a to (1L shl 41) + 1, b to (1L shl 41) - 1)
        var state = QuantumState(universes, 0, mapOf(universes to 1), null)
        val shuffle = listOf("e1d1", "e8d8", "d1e1", "d8e8")
        repeat(2) { for (uci in shuffle) state = QuantumVariant.apply(state, normal(uci)) }
        assertEquals(Color.WHITE, state.sideToMove)
        assertEquals(universes, state.universes)
        assertEquals(GameStatus.Finished(null, EndReason.THREEFOLD_REPETITION), QuantumVariant.status(state))
    }

    @Test
    fun universeMapsWithLongWeightsAreComparedByValue() {
        // Threefold repetition is keyed by the whole distribution, so Map<Board, Long> equality must hold
        // on every platform regardless of insertion order (Kotlin/JS hashes Long differently from the JVM).
        val state = superposedPawn().state as QuantumState
        val rebuilt = LinkedHashMap<Board, Long>()
        for ((b, w) in state.universes.entries.reversed()) rebuilt[b] = w * 3 / 3
        assertEquals(state.universes, rebuilt)
        assertEquals(state.universes.hashCode(), rebuilt.hashCode())
        assertEquals(1, state.positionCounts[rebuilt])
        val counts: Map<Map<Board, Long>, Int> = mapOf(rebuilt to 7)
        assertEquals(7, counts[state.universes])
        assertEquals(2L, state.totalWeight)
        assertEquals(1L, state.universes[state.universes.keys.first()])
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
    fun fiftyMoveRuleFiresAtOneHundredPlies() {
        fun withClock(halfmoveClock: Int): QuantumState {
            val initial = QuantumState.initial()
            return QuantumState(initial.universes, halfmoveClock, initial.positionCounts, null)
        }
        assertEquals(GameStatus.Ongoing, QuantumVariant.status(withClock(50)))
        assertEquals(GameStatus.Ongoing, QuantumVariant.status(withClock(99)))
        assertEquals(GameStatus.Finished(null, EndReason.FIFTY_MOVE_RULE), QuantumVariant.status(withClock(100)))
    }

    @Test
    fun negligibleUniversesAreDroppedOnceTheTotalExceedsTheThreshold() {
        val heavy = board("4k3/8/8/8/8/8/8/N3K3 w - - 0 1")
        val light = board("4k3/8/8/8/8/8/8/4K2N w - - 0 1")
        val universes = mapOf(heavy to (1L shl 50), light to 1L)
        val state = QuantumState(universes, 0, mapOf(universes to 1), null)
        // The knight on a1 splits in the heavy universe only; the light one passes with double weight.
        val next = QuantumVariant.apply(state, split("a1", "b3", "c2"))
        assertEquals(2, next.universeCount)
        assertTrue(next.boards().none { it[sq("h1")] != null }, "the 2^-50 universe should have been pruned")
        assertEquals(setOf(1L), next.universes.values.toSet())
    }

    @Test
    fun pruningNeverEmptiesTheState() {
        val heavy = board("4k3/8/8/8/8/8/8/N3K3 w - - 0 1")
        val light = board("4k3/8/8/8/8/8/8/4K2N w - - 0 1")
        val universes = mapOf(heavy to (1L shl 50) + 1, light to (1L shl 50) - 1)
        val state = QuantumState(universes, 0, mapOf(universes to 1), null)
        val next = QuantumVariant.apply(state, normal("e1d1"))
        assertTrue(next.universeCount >= 1)
        assertTrue(next.totalWeight > 0)
    }

    @Test
    fun evenlyWeightedUniversesAreNeverPruned() {
        // Pruning only drops universes below 2^-40 probability. Independent splits never merge, so n of
        // them produce 2^n equally weighted universes, and positionCounts retains every distribution so far.
        val game = Game(GameKind.QUANTUM)
        for (file in "abcd") {
            game.apply(split("${file}2", "${file}3", "${file}4"))
            game.apply(split("${file}7", "${file}6", "${file}5"))
        }
        val state = game.state as QuantumState
        assertEquals(1 shl 8, state.universeCount)
        assertEquals(1L shl 8, state.totalWeight)
        assertTrue(state.universes.values.all { it == 1L })
        assertEquals((1 shl 9) - 1, state.positionCounts.keys.sumOf { it.size })
        assertTrue(game.isLegal(normal("e2e4")))
    }

    @Test
    fun replayedGameIsIndistinguishableFromTheOriginal() {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("g1", "f3", "h3"))
        game.apply(split("g8", "f6", "h6"))
        game.apply(split("f3", "e5", "g5"))
        game.apply(normal("e7e6"))
        game.apply(observe("e5", null))
        game.apply(normal("d7d5"))
        val replayed = Game.replay(GameKind.QUANTUM, ChessJson.decodeMoves(ChessJson.encodeMoves(game.history)))
        val a = game.state as QuantumState
        val b = replayed.state as QuantumState
        assertEquals(a.universes, b.universes)
        assertEquals(a.universes.entries.toList(), b.universes.entries.toList(), "universe order must be reproducible")
        assertEquals(a.halfmoveClock, b.halfmoveClock)
        assertEquals(a.positionCounts, b.positionCounts)
        assertEquals(game.view(), replayed.view())
        assertEquals(game.status(), replayed.status())
        for (seed in 0 until 5) {
            assertEquals(game.resolve(GameMove.Observe(sq("h3")), Random(seed)), replayed.resolve(GameMove.Observe(sq("h3")), Random(seed)))
        }
    }
}
