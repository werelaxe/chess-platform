package ru.werelaxe.chess.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The [Game] facade in both variants: wrong side, finished games, illegal input, aliasing of internals. */
class GameTest {
    private fun fen(game: Game): String = (game.state as ClassicState).fen

    @Test
    fun movesForTheWrongSideAreRejectedInBothVariants() {
        for (kind in GameKind.entries) {
            val game = Game(kind)
            assertFalse(game.isLegal(normal("e7e5")), "$kind: black cannot move first")
            assertFailsWith<IllegalMoveException>("$kind") { game.apply(normal("e7e5")) }
            assertEquals(0, game.moveCount, "$kind")
            assertEquals(Color.WHITE, game.sideToMove, "$kind")
            assertTrue(game.legalTargets(sq("e7")).isEmpty(), "$kind")
        }
    }

    @Test
    fun movesAfterTheGameIsOverAreRejectedAndStateIsUntouched() {
        val game = Game(GameKind.CLASSIC).play("f2f3", "e7e5", "g2g4", "d8h4")
        val statusBefore = game.status()
        val viewBefore = game.view()
        val fenBefore = fen(game)
        assertTrue(statusBefore.isOver)
        assertFalse(game.isLegal(normal("a2a3")))
        assertFalse(game.isLegal(normal("e1f2")))
        assertFailsWith<IllegalMoveException> { game.apply(normal("a2a3")) }
        assertEquals(4, game.moveCount)
        assertEquals(Color.WHITE, game.sideToMove)
        assertEquals(statusBefore, game.status())
        assertEquals(viewBefore, game.view())
        assertEquals(fenBefore, fen(game))
    }

    @Test
    fun queriesReportNothingPlayableOnceTheGameIsOver() {
        val shuffle = arrayOf("g1f3", "g8f6", "f3g1", "f6g8")
        val classic = Game(GameKind.CLASSIC).play(*shuffle).play(*shuffle)
        assertTrue(classic.status().isOver)
        assertFalse(classic.isLegal(normal("e2e4")))
        assertTrue(classic.legalTargets(sq("e2")).isEmpty(), "legalTargets must agree with isLegal once the game is over")

        val quantum = Game(GameKind.QUANTUM)
        quantum.apply(split("e2", "e3", "e4"))
        val blackShuffle = arrayOf("g8f6", "g1f3", "f6g8", "f3g1")
        quantum.play(*blackShuffle).play(*blackShuffle)
        assertTrue(quantum.status().isOver)
        assertFalse(quantum.isLegal(observe("e4", null)))
        assertFalse(quantum.isLegal(normal("a7a6")))
        assertFalse(quantum.canObserve(sq("e4")), "canObserve must agree with isLegal once the game is over")
        assertTrue(quantum.legalTargets(sq("a7")).isEmpty())
        assertTrue(quantum.splitSecondTargets(sq("a7"), sq("a6")).isEmpty())
    }

    @Test
    fun illegalMovesLeaveAClassicGameUntouched() {
        val game = Game(GameKind.CLASSIC).play("e2e4")
        val before = fen(game)
        val attempts = listOf(
            normal("e4e6"),
            normal("e4e4"),
            normal("a1a3"),
            normal("e2e4"),
            normal("e7e4"),
            GameMove.Normal(sq("e7"), sq("e5"), PieceType.QUEEN),
            split("e7", "e6", "e5"),
            GameMove.Observe(sq("e4")),
            observe("e4", piece('P')),
        )
        for (move in attempts) {
            assertFalse(game.isLegal(move), "$move")
            assertFailsWith<IllegalMoveException>("$move") { game.apply(move) }
            assertEquals(before, fen(game), "$move")
            assertEquals(1, game.moveCount, "$move")
        }
    }

    @Test
    fun illegalMovesLeaveAQuantumGameUntouched() {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("e2", "e3", "e4"))
        val before = (game.state as QuantumState).universes.toMap()
        val attempts = listOf(
            normal("e7e7"),
            normal("e3e4"),
            normal("e2e4"),
            normal("e7e3"),
            split("e7", "e7", "e5"),
            split("e7", "e5", "e5"),
            split("e7", "e4", "e5"),
            split("e3", "e4", "e3"),
            GameMove.Observe(sq("e4")),
            observe("e4", piece('N')),
            observe("e4", piece('p')),
            observe("a7", null),
            observe("a7", piece('p')),
            observe("e2", null),
        )
        for (move in attempts) {
            assertFalse(game.isLegal(move), "$move")
            assertFailsWith<IllegalMoveException>("$move") { game.apply(move) }
            assertEquals(before, (game.state as QuantumState).universes, "$move")
            assertEquals(1, game.moveCount, "$move")
            assertEquals(Color.BLACK, game.sideToMove, "$move")
        }
    }

    @Test
    fun historyCannotBeMutatedThroughTheReturnedList() {
        val game = Game(GameKind.CLASSIC).play("e2e4")
        val leaked = game.history
        try {
            @Suppress("UNCHECKED_CAST")
            (leaked as MutableList<GameMove>).add(normal("e7e5"))
        } catch (e: UnsupportedOperationException) {
            // A read-only view is an acceptable implementation.
        } catch (e: ClassCastException) {
            // So is a type that is not a MutableList at all.
        }
        assertEquals(1, game.moveCount, "mutating the returned history must not affect the game")
        assertEquals(listOf(normal("e2e4")), game.history)
        assertEquals(game.view(), Game.replay(GameKind.CLASSIC, game.history).view(), "history must stay replayable to the current state")
    }

    @Test
    fun replayOfATamperedHistoryFails() {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("e2", "e3", "e4"))
        game.apply(normal("a7a6"))
        game.apply(observe("e4", piece('P')))
        val history = game.history

        // Impossible outcome written into the stored observation.
        assertFailsWith<IllegalMoveException> { Game.replay(GameKind.QUANTUM, history.dropLast(1) + observe("e4", piece('N'))) }
        // Outcome stripped from the stored observation.
        assertFailsWith<IllegalMoveException> { Game.replay(GameKind.QUANTUM, history.dropLast(1) + GameMove.Observe(sq("e4"))) }
        // A move for the wrong side inserted at the front.
        assertFailsWith<IllegalMoveException> { Game.replay(GameKind.QUANTUM, listOf(normal("e7e5")) + history) }
        // A move appended after the end of the game.
        val mate = Game(GameKind.CLASSIC).play("f2f3", "e7e5", "g2g4", "d8h4").history + normal("a2a3")
        assertFailsWith<IllegalMoveException> { Game.replay(GameKind.CLASSIC, mate) }
        // A quantum move in a classic history.
        assertFailsWith<IllegalMoveException> { Game.replay(GameKind.CLASSIC, listOf(split("e2", "e3", "e4"))) }
        assertFailsWith<IllegalMoveException> { Game.replay(GameKind.CLASSIC, listOf(normal("e2e4"), GameMove.Observe(sq("e4")))) }
        // The untampered prefix still replays.
        val fine = Game.replay(GameKind.QUANTUM, history.take(2))
        assertEquals(2, fine.moveCount)
        assertEquals(2, (fine.state as QuantumState).universeCount)
    }
}
