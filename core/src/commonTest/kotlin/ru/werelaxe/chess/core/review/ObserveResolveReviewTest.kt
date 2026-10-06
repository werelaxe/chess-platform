package ru.werelaxe.chess.core.review

import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
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

class ObserveResolveReviewTest {
    /** Knight on h3 with probability 1/2, on e5 and g5 with probability 1/4 each. */
    private fun knightGame(): Game {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("g1", "f3", "h3"))
        game.apply(normal("a7a6"))
        game.apply(split("f3", "e5", "g5"))
        game.apply(normal("a6a5"))
        return game
    }

    // EXPECTED TO FAIL UNTIL FIXED: the authoritative side must draw the outcome itself; a client
    // that proposes an observation with an outcome already filled in currently dictates the result.
    @Test
    fun resolveMustNotTrustAClientSuppliedOutcome() {
        val game = knightGame()
        val proposed = observe("e5", piece('N')) // the unlikely outcome (1/4), chosen by the "client"
        val outcomes = (1..400).map { seed ->
            (game.resolve(proposed, Random(seed)) as GameMove.Observe).outcome?.piece
        }
        val empty = outcomes.count { it == null }
        assertTrue(empty in 220..380, "expected roughly 3/4 of resolutions to find e5 empty, got $empty of 400")
    }

    @Test
    fun samplingMatchesAnUnevenDistribution() {
        val state = knightGame().state as QuantumState
        assertEquals(0.25, state.distribution(sq("e5"))[piece('N')])
        val random = Random(11)
        var knights = 0
        repeat(4000) { if (QuantumVariant.sampleContent(state, sq("e5"), random) != null) knights++ }
        assertTrue(knights in 850..1150, "expected about a quarter of the samples to see the knight, got $knights")
    }
}
