package ru.werelaxe.chess.core.review

import ru.werelaxe.chess.core.ChessJson
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.QuantumState
import ru.werelaxe.chess.core.normal
import ru.werelaxe.chess.core.split
import ru.werelaxe.chess.core.sq
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Performance probe: independent splits never merge, so n of them produce 2^n distinct
 * universes, and every facade call is linear in that number. Prints the per-call cost so the
 * numbers can be compared between JVM and JS.
 */
class ApiUniverseGrowthReviewTest {
    private inline fun <T> timed(label: String, block: () -> T): Pair<T, Duration> {
        val mark = TimeSource.Monotonic.markNow()
        val result = block()
        val elapsed = mark.elapsedNow()
        println("[review] $label: $elapsed")
        return result to elapsed
    }

    @Test
    fun fourteenOpeningSplitsProduce16384UniversesAndEveryCallScalesWithThem() {
        // Seven files only, so the test stays under the 2 s per-test limit of the JS runner;
        // with all eight files the same loop ends at 65536 universes.
        val game = Game(GameKind.QUANTUM)
        val perTurn = LinkedHashMap<Int, Duration>()
        for (file in "abcdefg") {
            for (uci in listOf("${file}2|${file}3|${file}4", "${file}7|${file}6|${file}5")) {
                val (from, first, second) = uci.split("|")
                val move = split(from, first, second)
                val (_, legal) = timed("isLegal(split) @${(game.state as QuantumState).universeCount}") { game.isLegal(move) }
                val (_, applied) = timed("apply(split)") { game.apply(move) }
                val count = (game.state as QuantumState).universeCount
                val (_, status) = timed("status() @$count") { game.status() }
                val (_, view) = timed("viewJson() @$count") { ChessJson.encodeView(game.view()) }
                perTurn[count] = legal + applied + status + view
            }
        }
        val state = game.state as QuantumState
        assertEquals(16384, state.universeCount)
        assertEquals(16384L, state.totalWeight)

        val count = state.universeCount
        timed("legalTargets(g1) @$count") { game.legalTargets(sq("g1")) }
        timed("splitSecondTargets(g1,f3) @$count") { game.splitSecondTargets(sq("g1"), sq("f3")) }
        timed("canObserve(e4) @$count") { game.canObserve(sq("e4")) }
        timed("isLegal(normal g1f3) @$count") { game.isLegal(normal("g1f3")) }
        timed("apply(normal g1f3) @$count") { game.apply(normal("g1f3")) }
        timed("status() @$count") { game.status() }
        timed("replay(15 moves)") { Game.replay(GameKind.QUANTUM, game.history.toList()) }

        println("[review] per-turn facade cost (isLegal+apply+status+view) by universe count: $perTurn")
        // Something a browser can live with: one full turn of facade calls after an ordinary 14-split opening.
        val lastTurn = perTurn.getValue(16384)
        assertTrue(lastTurn < Duration.parse("10s"), "one turn of facade calls took $lastTurn at 16384 universes")
    }

    @Test
    fun universeCountIsNotBoundedByPruning() {
        // Pruning only drops universes below 2^-40 probability; evenly weighted universes are never pruned.
        val game = Game(GameKind.QUANTUM)
        for (file in "abcd") {
            game.apply(split("${file}2", "${file}3", "${file}4"))
            game.apply(split("${file}7", "${file}6", "${file}5"))
        }
        game.apply(split("g1", "f3", "h3"))
        game.apply(split("g8", "f6", "h6"))
        // "Stay" splits of the rooks: a2/a7 are empty in every universe after the a-pawn splits.
        game.apply(split("a1", "a2", "a1"))
        game.apply(split("a8", "a7", "a8"))
        val state = game.state as QuantumState
        assertEquals(1 shl 12, state.universeCount)
        assertTrue(state.universes.values.all { it == 1L })
        // The history is the only persisted form: 12 moves = 12 * 2^k boards retained in positionCounts.
        val retained = state.positionCounts.keys.sumOf { it.size }
        println("[review] boards retained by positionCounts after 12 splits: $retained")
        assertEquals((1 shl 13) - 1, retained)
        val move: GameMove = normal("e2e4")
        assertTrue(game.isLegal(move))
    }
}
