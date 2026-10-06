package ru.werelaxe.chess.core.review

import ru.werelaxe.chess.core.Board
import ru.werelaxe.chess.core.ChessJson
import ru.werelaxe.chess.core.ClassicRules
import ru.werelaxe.chess.core.Fen
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.QuantumState
import ru.werelaxe.chess.core.Square
import ru.werelaxe.chess.core.board
import ru.werelaxe.chess.core.mv
import ru.werelaxe.chess.core.normal
import ru.werelaxe.chess.core.split
import ru.werelaxe.chess.core.sq
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs on both JVM and JS (commonTest). Exercises the places where Kotlin/JS differs from the
 * JVM: Long as map values, lazily cached hash codes, value classes in hashed collections.
 */
class ApiPlatformCollectionsReviewTest {
    @Test
    fun boardsBuiltDifferentlyHashAndCompareEqual() {
        var played = Board.initial()
        for (uci in listOf("e2e4", "a7a6", "e4e5", "d7d5")) played = ClassicRules.apply(played, mv(uci))
        val parsed = board("rnbqkbnr/1pp1pppp/p7/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3")
        assertEquals(parsed, played)
        assertEquals(parsed.hashCode(), played.hashCode())
        assertEquals(sq("d6"), played.enPassant)
        assertEquals(Fen.format(parsed), Fen.format(played))
    }

    @Test
    fun universeMapsWithLongWeightsAreComparedByValue() {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("e2", "e3", "e4"))
        game.apply(normal("a7a6"))
        val state = game.state as QuantumState
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
    fun squaresBehaveAsValuesInsideCollections() {
        val set = LinkedHashSet<Square>()
        set.add(Square(27))
        set.add(sq("d4"))
        set.add(Square.of(3, 3))
        assertEquals(1, set.size)
        assertTrue(Square.parse("d4") in set)
        val map = HashMap<Square, Int>()
        map[Square(0)] = 1
        map[sq("a1")] = 2
        assertEquals(mapOf(Square.A1 to 2), map)
        assertEquals(Square.ALL.toSet(), (0 until 64).map { Square(it) }.toSet())
        assertEquals(listOf(27), listOf(sq("d4")).map { it.index })
        assertEquals("\"d4\"", ChessJson.json.encodeToString(Square.serializer(), sq("d4")))
        assertEquals(sq("d4"), ChessJson.json.decodeFromString(Square.serializer(), "\"d4\""))
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
}

class ApiBoardSerializationReviewTest {
    @Test
    fun delegatedHashCacheDoesNotLeakIntoBoardJson() {
        val json = ChessJson.json.encodeToString(Board.serializer(), Board.initial())
        assertTrue("cachedHash" !in json && "delegate" !in json, json)
        val decoded = ChessJson.json.decodeFromString(Board.serializer(), json)
        assertEquals(Board.initial(), decoded)
        assertEquals(Board.initial().hashCode(), decoded.hashCode())
    }
}
