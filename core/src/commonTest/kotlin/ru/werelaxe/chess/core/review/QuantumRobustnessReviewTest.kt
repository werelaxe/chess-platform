package ru.werelaxe.chess.core.review

import ru.werelaxe.chess.core.ClassicState
import ru.werelaxe.chess.core.ClassicVariant
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.IllegalMoveException
import ru.werelaxe.chess.core.Observation
import ru.werelaxe.chess.core.Piece
import ru.werelaxe.chess.core.PieceType
import ru.werelaxe.chess.core.QuantumState
import ru.werelaxe.chess.core.QuantumVariant
import ru.werelaxe.chess.core.board
import ru.werelaxe.chess.core.normal
import ru.werelaxe.chess.core.observe
import ru.werelaxe.chess.core.piece
import ru.werelaxe.chess.core.split
import ru.werelaxe.chess.core.sq
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/** Quantum-specific misuse: forged observation outcomes, promotion garbage, state blow-up. */
class QuantumRobustnessReviewTest {
    private fun splitGame(): Game {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("e2", "e3", "e4"))
        game.apply(normal("a7a6"))
        return game
    }

    @Test
    fun resolveSamplesTheOutcomeEvenWhenTheClientSuppliedOne() {
        // The spec (ARCHITECTURE 1.2, 2.4) makes Game.resolve the step where the authoritative side draws
        // the observation outcome. A client may send {"type":"observe","square":"e4","outcome":{...}} with an
        // outcome of its choosing; resolve must replace it with a sample, otherwise the client decides the collapse.
        val game = splitGame()
        val state = game.state as QuantumState
        for (seed in 0 until 8) {
            val honest = QuantumVariant.sampleContent(state, sq("e4"), Random(seed))
            val forged = if (honest == null) piece('P') else null
            val resolved = game.resolve(observe("e4", forged), Random(seed)) as GameMove.Observe
            assertEquals(Observation(honest), resolved.outcome, "seed $seed: the authoritative side must not trust a client-chosen outcome")
        }
    }

    @Test
    fun forgedOutcomeIsAtLeastValidatedAgainstTheDistribution() {
        val game = splitGame()
        val impossible = observe("e4", piece('N'))
        assertFalse(game.isLegal(impossible))
        assertFailsWith<IllegalMoveException> { game.apply(impossible) }
        val resolved = game.resolve(impossible, Random(1)) as GameMove.Observe
        assertTrue(resolved.outcome?.piece != piece('N'), "resolve must replace a client-supplied outcome")
        assertTrue(game.isLegal(resolved))
        assertEquals(2, game.moveCount)
    }

    @Test
    fun observeWithoutUncertaintyIsNotResolved() {
        val game = splitGame()
        val resolved = game.resolve(GameMove.Observe(sq("a6")), Random(3)) as GameMove.Observe
        assertEquals(null, resolved.outcome)
        assertFalse(game.isLegal(resolved))
        assertFailsWith<IllegalMoveException> { game.apply(resolved) }
    }

    @Test
    fun splitWithPromotionOnNonPawnIgnoresThePromotion() {
        val game = Game(GameKind.QUANTUM)
        assertTrue(game.isLegal(split("g1", "f3", "h3", PieceType.QUEEN)))
        game.apply(split("g1", "f3", "h3", PieceType.QUEEN))
        val state = game.state as QuantumState
        assertEquals(0.5, state.distribution(sq("f3"))[piece('N')])
        assertEquals(0.5, state.distribution(sq("h3"))[piece('N')])
        val queen = Piece(Color.WHITE, PieceType.QUEEN)
        assertTrue(state.universes.keys.all { board -> board.squares.count { it == queen } == 1 }, "no queen may appear")
    }

    @Test
    fun promotionToKingOrPawnIsRejected() {
        val fen = "4k3/P7/8/8/8/8/8/4K3 w - - 0 1"
        val start = QuantumState.of(board(fen))
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
    fun observeOnASquareWithTwoDifferentPiecesIsPossible() {
        // White knight b1 goes to c3 or stays; black knight b8 goes to c6 or stays; then white Nc3xd5?
        // Simpler: both colours can occupy e4 - white pawn (e2e4 branch) or black pawn (after e7e5 and e5e4 in the other branch).
        val game = Game(GameKind.QUANTUM)
        game.apply(split("e2", "e4", "e2"))
        game.apply(normal("e7e5"))
        game.apply(normal("a2a3"))
        game.apply(normal("e5e4")) // legal only where e4 is empty
        val state = game.state as QuantumState
        val distribution = state.distribution(sq("e4"))
        assertEquals(0.5, distribution[piece('P')])
        assertEquals(0.5, distribution[piece('p')])
        assertTrue(game.canObserve(sq("e4")))
        assertTrue(game.isLegal(observe("e4", piece('p'))))
        assertTrue(game.isLegal(observe("e4", piece('P'))))
        assertFalse(game.isLegal(observe("e4", null)))
    }

    @Test
    fun universeCountGrowsExponentiallyWithIndependentSplits() {
        // Documents the absence of any bound on the state size: 16 plies of pawn splits already yield 2^16
        // equally likely universes, none of which the 2^-40 pruning can ever drop.
        val game = Game(GameKind.QUANTUM)
        val mark = TimeSource.Monotonic.markNow()
        for (file in "abcdefgh") {
            game.apply(split("${file}2", "${file}3", "${file}4"))
            game.apply(split("${file}7", "${file}6", "${file}5"))
        }
        val applyTime = mark.elapsedNow()
        val state = game.state as QuantumState
        assertEquals(65536, state.universeCount)
        val statusMark = TimeSource.Monotonic.markNow()
        val status = game.status()
        val statusTime = statusMark.elapsedNow()
        val viewMark = TimeSource.Monotonic.markNow()
        game.view()
        val viewTime = viewMark.elapsedNow()
        val targetsMark = TimeSource.Monotonic.markNow()
        game.legalTargets(sq("g1"))
        val targetsTime = targetsMark.elapsedNow()
        println("REVIEW: 16 pawn splits -> ${state.universeCount} universes; apply $applyTime, status $statusTime ($status), view $viewTime, legalTargets $targetsTime, retained distributions ${state.positionCounts.keys.sumOf { it.size }}")
    }
}
