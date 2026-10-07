package ru.werelaxe.chess.core.engine

import ru.werelaxe.chess.core.ClassicRules
import ru.werelaxe.chess.core.ClassicState
import ru.werelaxe.chess.core.ClassicVariant
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.GameStatus
import ru.werelaxe.chess.core.QuantumState
import ru.werelaxe.chess.core.QuantumVariant
import ru.werelaxe.chess.core.board
import ru.werelaxe.chess.core.normal
import ru.werelaxe.chess.core.split
import ru.werelaxe.chess.core.sq
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class ChessEngineTest {
    private fun classic(fen: String, level: EngineLevel = EngineLevel.MEDIUM): GameMove.Normal {
        val move = ChessEngine(level, Random(1)).chooseMove(ClassicState.fromFen(fen))
        return assertNotNull(move) as GameMove.Normal
    }

    @Test
    fun findsMateInOne() {
        assertEquals(normal("a1a8"), classic("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1"))
    }

    @Test
    fun takesAHangingQueen() {
        assertEquals(normal("d1d5"), classic("4k3/8/8/3q4/8/8/8/3RK3 w - - 0 1"))
    }

    @Test
    fun defendsAgainstMateInOne() {
        // After 1.e4 e5 2.Bc4 Nc6 3.Qh5 Black must cover f7.
        val fen = "r1bqkbnr/pppp1ppp/2n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 3 3"
        val state = ClassicState.fromFen(fen)
        val reply = classic(fen)
        val after = ClassicVariant.apply(state, reply)
        for (white in ClassicRules.legalMoves(after.board)) {
            val next = ClassicVariant.apply(after, GameMove.Normal(white.from, white.to, white.promotion))
            assertTrue(ClassicVariant.status(next) != GameStatus.Finished(Color.WHITE, EndReason.CHECKMATE), "engine allowed mate by ${white.uci}")
        }
    }

    @Test
    fun easyLevelPlaysLegalMovesQuickly() {
        val game = Game(GameKind.CLASSIC)
        val engine = ChessEngine(EngineLevel.EASY, Random(3))
        repeat(30) {
            if (game.isOver) return
            val start = TimeSource.Monotonic.markNow()
            val move = assertNotNull(engine.chooseMove(game))
            assertTrue(start.elapsedNow() < EngineLevel.EASY.timeBudget * 4, "move took ${start.elapsedNow()}")
            assertTrue(game.isLegal(move), "engine proposed an illegal move $move")
            game.apply(move)
        }
    }

    @Test
    fun hardLevelRespectsItsTimeBudget() {
        val start = TimeSource.Monotonic.markNow()
        val move = ChessEngine(EngineLevel.HARD, Random(1)).chooseMove(ClassicState.initial())
        assertNotNull(move)
        assertTrue(start.elapsedNow() < EngineLevel.HARD.timeBudget * 2, "move took ${start.elapsedNow()}")
    }

    @Test
    fun capturesTheKingWhereItWasLeftInCheck() {
        // White Ke1 Rh1, Black Ke8: the rook splits to h8 (check) or stays; Kd8 is legal only where it stayed.
        var state = QuantumState.of(board("4k3/8/8/8/8/8/8/4K2R w - - 0 1"))
        state = QuantumVariant.apply(state, split("h1", "h8", "h1"))
        state = QuantumVariant.apply(state, normal("e8d8"))
        val move = ChessEngine(EngineLevel.MEDIUM, Random(1)).chooseMove(state)
        assertEquals(GameMove.Normal(sq("h8"), sq("e8")), move)
    }

    @Test
    fun quantumSelfPlayStaysLegal() {
        val game = Game(GameKind.QUANTUM)
        val engine = ChessEngine(EngineLevel.EASY, Random(5))
        var splits = 0
        repeat(16) {
            if (game.isOver) return
            val start = TimeSource.Monotonic.markNow()
            val move = assertNotNull(engine.chooseMove(game))
            assertTrue(start.elapsedNow() < EngineLevel.EASY.timeBudget * 6, "move took ${start.elapsedNow()}")
            if (move is GameMove.Split) splits++
            val resolved = game.resolve(move, Random(7))
            assertTrue(game.isLegal(resolved), "engine proposed an illegal move $move")
            game.apply(resolved)
        }
        val state = game.state as QuantumState
        assertTrue(state.universeCount >= 1)
    }

    @Test
    fun quantumEvaluationPrefersKeepingTheKing() {
        val search = QuantumSearch(4, TimeSource.Monotonic.markNow() + EngineLevel.MEDIUM.timeBudget, 0, Random(1))
        val both = QuantumState.of(board("4k3/8/8/8/8/8/8/4K3 w - - 0 1"))
        val kingless = QuantumState.of(board("4k3/8/8/8/8/8/8/8 w - - 0 1"))
        assertTrue(search.evaluate(kingless, Color.WHITE) < search.evaluate(both, Color.WHITE) - Evaluation.KING_PRESENCE / 2)
        assertTrue(search.evaluate(kingless, Color.BLACK) > search.evaluate(both, Color.BLACK) + Evaluation.KING_PRESENCE / 2)
    }
}
