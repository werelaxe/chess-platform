package ru.werelaxe.chess.engine

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
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * What the engine must do whichever way its work is spread over threads: the same scores as
 * the sequential search (a different move only among equally scored ones), legal play, and
 * the time budget. Subclasses supply the [parallelism] under test.
 */
abstract class SearchParallelismContract(private val parallelism: SearchParallelism) {
    private fun engine(level: EngineLevel, seed: Int = 1) = ChessEngine(level, Random(seed), parallelism)

    private fun classic(fen: String, level: EngineLevel = EngineLevel.MEDIUM): GameMove.Normal =
        assertNotNull(engine(level).chooseMove(ClassicState.fromFen(fen))) as GameMove.Normal

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
        val fen = "r1bqkbnr/pppp1ppp/2n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 3 3"
        val state = ClassicState.fromFen(fen)
        val after = ClassicVariant.apply(state, classic(fen))
        for (white in ClassicRules.legalMoves(after.board)) {
            val next = ClassicVariant.apply(after, GameMove.Normal(white.from, white.to, white.promotion))
            assertTrue(ClassicVariant.status(next) != GameStatus.Finished(Color.WHITE, EndReason.CHECKMATE), "engine allowed mate by ${white.uci}")
        }
    }

    @Test
    fun classicScoresMatchTheSequentialSearch() {
        val positions = listOf(
            ClassicState.initial().board to 4,
            board(KIWIPETE) to 4,
            board("r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4") to 4,
            board("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1") to 5,
        )
        for ((board, depth) in positions) {
            val deadline = TimeSource.Monotonic.markNow() + 10.minutes
            val sequential = assertNotNull(ClassicSearch(depth, true, deadline, 0, Random(1)).bestMove(board))
            val parallel = assertNotNull(ClassicSearch(depth, true, deadline, 0, Random(1), parallelism).bestMove(board))
            assertEquals(sequential.depth, parallel.depth, "depth in $board")
            assertEquals(sequential.score, parallel.score, "score in $board")
            assertTrue(parallel.move in ClassicRules.legalMoves(board), "illegal move ${parallel.move.uci} in $board")
        }
    }

    @Test
    fun quantumChoicesMatchTheSequentialSearch() {
        val deadline = TimeSource.Monotonic.markNow() + 10.minutes
        for ((state, level) in listOf(quantumPosition(60) to EngineLevel.MEDIUM, quantumPosition(30, seed = 5) to EngineLevel.EASY)) {
            val sequential = QuantumSearch(level.quantumCandidates, level.quantumDepth, deadline, 10.minutes, level.randomness, Random(1)).bestMove(state)
            val parallel = QuantumSearch(level.quantumCandidates, level.quantumDepth, deadline, 10.minutes, level.randomness, Random(1), parallelism).bestMove(state)
            assertEquals(sequential, parallel)
        }
    }

    @Test
    fun classicSelfPlayStaysLegal() {
        val game = Game(GameKind.CLASSIC)
        val engine = engine(EngineLevel.EASY, seed = 3)
        repeat(30) {
            if (game.isOver) return
            val move = assertNotNull(engine.chooseMove(game))
            assertTrue(game.isLegal(move), "engine proposed an illegal move $move")
            game.apply(move)
        }
    }

    @Test
    fun quantumSelfPlayStaysLegal() {
        val game = Game(GameKind.QUANTUM)
        val engine = engine(EngineLevel.EASY, seed = 5)
        repeat(16) {
            if (game.isOver) return
            val move = assertNotNull(engine.chooseMove(game))
            val resolved = game.resolve(move, Random(7))
            assertTrue(game.isLegal(resolved), "engine proposed an illegal move $move")
            game.apply(resolved)
        }
    }

    @Test
    fun capturesTheKingWhereItWasLeftInCheck() {
        var state = QuantumState.of(board("4k3/8/8/8/8/8/8/4K2R w - - 0 1"))
        state = QuantumVariant.apply(state, split("h1", "h8", "h1"))
        state = QuantumVariant.apply(state, normal("e8d8"))
        assertEquals(GameMove.Normal(sq("h8"), sq("e8")), engine(EngineLevel.MEDIUM).chooseMove(state))
    }

    @Test
    fun hardLevelRespectsItsTimeBudget() {
        val start = TimeSource.Monotonic.markNow()
        assertNotNull(engine(EngineLevel.HARD).chooseMove(ClassicState.fromFen(KIWIPETE)))
        assertTrue(start.elapsedNow() < EngineLevel.HARD.timeBudget * 2, "move took ${start.elapsedNow()}")
    }

    @Test
    fun stopsAtTheDeadline() {
        val start = TimeSource.Monotonic.markNow()
        val classic = assertNotNull(ClassicSearch(8, true, start + 300.milliseconds, 0, Random(1), parallelism).bestMove(board(KIWIPETE)))
        assertTrue(start.elapsedNow() < 1.seconds, "the classic search took ${start.elapsedNow()}")
        assertTrue(classic.depth in 1 until 8, "reached depth ${classic.depth}")

        val state = quantumPosition(300)
        val started = TimeSource.Monotonic.markNow()
        assertNotNull(QuantumSearch(24, 4, started + 100.milliseconds, 100.milliseconds, 0, Random(1), parallelism).bestMove(state))
        assertTrue(started.elapsedNow() < 1.seconds, "the quantum search took ${started.elapsedNow()}")
    }
}

/** Runs the tasks one after another but last first: the order a thread pool may happen to use, without threads. */
object ReverseOrder : SearchParallelism {
    override fun <T, R> map(items: List<T>, task: (T) -> R): List<R> = items.asReversed().map(task).asReversed()

    override fun sharedBound(initial: Int): SharedBound = PlainBound(initial)
}

class ReverseOrderSearchTest : SearchParallelismContract(ReverseOrder)
