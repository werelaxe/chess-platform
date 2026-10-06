package ru.werelaxe.chess.core.review

import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.Observation
import ru.werelaxe.chess.core.QuantumState
import ru.werelaxe.chess.core.QuantumVariant
import ru.werelaxe.chess.core.normal
import ru.werelaxe.chess.core.observe
import ru.werelaxe.chess.core.piece
import ru.werelaxe.chess.core.split
import ru.werelaxe.chess.core.sq
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ARCHITECTURE.md 1.2/1.3: "The server decides the outcome at random according to the current
 * distribution; a client proposes the move with outcome = null", and 2.4 describes the server
 * pipeline as resolve -> isLegal -> apply. [Game.resolve] is therefore the only place where the
 * authoritative side can enforce randomness; if it keeps an outcome that came from the client,
 * the client can choose which universes survive.
 */
class ApiObservationOutcomeReviewTest {
    private fun superposedPawn(): Game {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("e2", "e3", "e4"))
        game.apply(normal("a7a6"))
        return game
    }

    @Test
    fun resolveSamplesTheOutcomeEvenWhenTheClientSuppliedOne() {
        val game = superposedPawn()
        val state = game.state as QuantumState
        val forged = observe("e4", piece('P'))
        for (seed in 0 until 40) {
            val expected = Observation(QuantumVariant.sampleContent(state, sq("e4"), Random(seed)))
            val resolved = game.resolve(forged, Random(seed)) as GameMove.Observe
            assertEquals(expected, resolved.outcome, "seed $seed: the authoritative side must sample the outcome itself")
        }
    }

    @Test
    fun serverPipelineCannotBeSteeredByAForgedOutcome() {
        // A client that always sends outcome = "pawn on e4" currently wins the coin flip every time.
        var pawnSeen = 0
        for (seed in 0 until 40) {
            val game = superposedPawn()
            val resolved = game.resolve(observe("e4", piece('P')), Random(seed))
            assertTrue(game.isLegal(resolved))
            game.apply(resolved)
            if ((game.state as QuantumState).distribution(sq("e4"))[piece('P')] == 1.0) pawnSeen++
        }
        assertTrue(pawnSeen in 8..32, "a fair observation collapses to the pawn about half of the time, got $pawnSeen/40")
    }

    @Test
    fun resolveFillsAMissingOutcomeAndLeavesOtherMovesAlone() {
        val game = superposedPawn()
        val resolved = game.resolve(GameMove.Observe(sq("e4")), Random(3)) as GameMove.Observe
        assertTrue(resolved.outcome != null)
        assertTrue(game.isLegal(resolved))
        assertEquals(normal("g1f3"), game.resolve(normal("g1f3"), Random(3)))
        // A square with a certain content cannot be observed; resolve leaves the move unresolved.
        assertEquals(GameMove.Observe(sq("e1")), game.resolve(GameMove.Observe(sq("e1")), Random(3)))
    }
}
