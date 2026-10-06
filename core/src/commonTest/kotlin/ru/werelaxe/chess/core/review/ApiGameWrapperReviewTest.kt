package ru.werelaxe.chess.core.review

import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.GameStatus
import ru.werelaxe.chess.core.IllegalMoveException
import ru.werelaxe.chess.core.QuantumState
import ru.werelaxe.chess.core.normal
import ru.werelaxe.chess.core.observe
import ru.werelaxe.chess.core.piece
import ru.werelaxe.chess.core.play
import ru.werelaxe.chess.core.split
import ru.werelaxe.chess.core.sq
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiGameWrapperReviewTest {
    @Test
    fun nothingIsLegalOnceTheGameIsOver() {
        val game = Game(GameKind.CLASSIC).play("f2f3", "e7e5", "g2g4", "d8h4")
        assertTrue(game.status().isOver)
        assertFalse(game.isLegal(normal("a2a3")))
        assertFalse(game.isLegal(normal("e1f2")))
        assertFailsWith<IllegalMoveException> { game.apply(normal("a2a3")) }
        assertEquals(4, game.moveCount)
        assertEquals(Color.WHITE, game.sideToMove)
    }

    @Test
    fun quantumGameOverBlocksObservationsToo() {
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
    fun helperQueriesAgreeWithIsLegalOnceTheGameIsOver() {
        // White's queen gives check in one universe only; black ignores it there, the king is captured
        // there, and an observation of e8 collapses onto that universe: black loses by KING_CAPTURED
        // while still owning every pawn.
        val game = Game(GameKind.QUANTUM)
        game.apply(normal("e2e4"))
        game.apply(normal("f7f6"))
        game.apply(split("d1", "h5", "d1"))
        game.apply(normal("a7a6"))
        game.apply(normal("h5e8"))
        game.apply(normal("a6a5"))
        game.apply(observe("e8", piece('Q')))
        assertEquals(GameStatus.Finished(Color.WHITE, EndReason.KING_CAPTURED), game.status())
        assertEquals(Color.BLACK, game.sideToMove)
        // Black's pawn moves were illegal in the checked universe, so the surviving universe still has the pawn on a7.
        assertEquals(1, (game.state as QuantumState).universeCount)
        assertEquals(piece('p'), (game.state as QuantumState).universes.keys.single()[sq("a7")])
        assertFalse(game.isLegal(normal("a7a6")))
        assertFalse(game.isLegal(split("a7", "a6", "a7")))
        assertFailsWith<IllegalMoveException> { game.apply(normal("a7a6")) }
        // Every square the helpers offer must be playable; after the game is over nothing is.
        assertEquals(emptySet(), game.legalTargets(sq("a7")), "legalTargets after game over")
        assertEquals(emptySet(), game.splitSecondTargets(sq("a7"), sq("a6")), "splitSecondTargets after game over")
    }

    @Test
    fun canObserveAgreesWithIsLegalOnceTheGameIsOver() {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("e2", "e3", "e4"))
        game.apply(normal("f7f6"))
        game.apply(normal("a2a3"))
        game.apply(normal("g7g5"))
        game.apply(normal("d1h5"))
        assertEquals(GameStatus.Finished(Color.WHITE, EndReason.CHECKMATE), game.status())
        assertFalse(game.isLegal(observe("e4", piece('P'))))
            assertFalse(game.canObserve(sq("e4")), "canObserve after game over")
    }

    @Test
    fun replayStopsAtTheFirstBadMove() {
        val history = listOf(split("e2", "e3", "e4"), normal("a7a6"), GameMove.Observe(sq("e4")), normal("a6a5"))
        assertFailsWith<IllegalMoveException> { Game.replay(GameKind.QUANTUM, history) }
        assertFailsWith<IllegalMoveException> { Game.replay(GameKind.CLASSIC, listOf(split("e2", "e3", "e4"))) }
        assertFailsWith<IllegalMoveException> { Game.replay(GameKind.CLASSIC, listOf(observe("e2", piece('P')))) }
        val fine = Game.replay(GameKind.QUANTUM, history.take(2))
        assertEquals(2, fine.moveCount)
        assertEquals(2, (fine.state as QuantumState).universeCount)
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
        val replayed = Game.replay(GameKind.QUANTUM, game.history.toList())
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

    @Test
    fun legalTargetsAndSplitTargetsAgreeWithIsLegal() {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("e2", "e3", "e4"))
        game.apply(normal("d7d5"))
        for (from in ru.werelaxe.chess.core.Square.ALL) {
            val targets = game.legalTargets(from)
            for (to in ru.werelaxe.chess.core.Square.ALL) {
                val promotion = if (game.requiresPromotion(from, to)) ru.werelaxe.chess.core.PieceType.QUEEN else null
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
}
