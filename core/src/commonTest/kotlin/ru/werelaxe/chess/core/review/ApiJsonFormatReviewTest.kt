package ru.werelaxe.chess.core.review

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.werelaxe.chess.core.CellEntry
import ru.werelaxe.chess.core.ChessJson
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.GameStatus
import ru.werelaxe.chess.core.Observation
import ru.werelaxe.chess.core.PieceType
import ru.werelaxe.chess.core.normal
import ru.werelaxe.chess.core.observe
import ru.werelaxe.chess.core.piece
import ru.werelaxe.chess.core.split
import ru.werelaxe.chess.core.sq
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Checks the wire formats of ARCHITECTURE.md 1.3 key by key. */
class ApiJsonFormatReviewTest {
    private fun parse(text: String): JsonObject = ChessJson.json.parseToJsonElement(text).jsonObject

    @Test
    fun boardViewHasExactlyTheDocumentedKeys() {
        val view = parse(ChessJson.encodeView(Game(GameKind.QUANTUM).view()))
        assertEquals(setOf("sideToMove", "cells", "universeCount", "checkProbability", "lastMove"), view.keys)
        assertEquals(JsonNull, view["lastMove"])
        assertEquals(JsonPrimitive(1), view["universeCount"])
        val cells = view["cells"]!!.jsonArray
        assertEquals(64, cells.size)
        // Derived properties of CellView (isEmpty, isClassical) must not leak into the JSON.
        for (cell in cells) assertEquals(setOf("square", "entries"), cell.jsonObject.keys)
        assertEquals("a1", cells[0].jsonObject["square"]!!.jsonPrimitive.content)
        assertEquals("h8", cells[63].jsonObject["square"]!!.jsonPrimitive.content)
        val a1 = cells[0].jsonObject["entries"]!!.jsonArray.single().jsonObject
        assertEquals(setOf("piece", "probability"), a1.keys)
        assertEquals("ROOK", a1["piece"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(1.0, a1["probability"]!!.jsonPrimitive.content.toDouble())
        assertEquals(JsonArray(emptyList()), cells[sq("e4").index].jsonObject["entries"])
    }

    @Test
    fun quantumViewEncodesEmptyEntryWithNullPieceAndSortsByProbability() {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("g1", "f3", "h3"))
        game.apply(normal("a7a6"))
        game.apply(split("f3", "e5", "g5"))
        val view = parse(ChessJson.encodeView(game.view()))
        val h3 = view["cells"]!!.jsonArray[sq("h3").index].jsonObject["entries"]!!.jsonArray
        assertEquals(2, h3.size)
        assertEquals("KNIGHT", h3[0].jsonObject["piece"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, h3[1].jsonObject["piece"])
        val e5 = view["cells"]!!.jsonArray[sq("e5").index].jsonObject["entries"]!!.jsonArray
        // Empty (0.75) comes before the knight (0.25): sorted by decreasing probability.
        assertEquals(JsonNull, e5[0].jsonObject["piece"])
        assertEquals(0.75, e5[0].jsonObject["probability"]!!.jsonPrimitive.content.toDouble())
        assertEquals(0.25, e5[1].jsonObject["probability"]!!.jsonPrimitive.content.toDouble())
        val lastMove = view["lastMove"]!!.jsonObject
        assertEquals("split", lastMove["type"]!!.jsonPrimitive.content)
        assertEquals(setOf("type", "from", "first", "second"), lastMove.keys)
        assertEquals(JsonPrimitive(3), view["universeCount"])
    }

    @Test
    fun movesDecodeFromTheDocumentedShapes() {
        assertEquals(normal("e2e4"), ChessJson.decodeMove("""{"type":"normal","from":"e2","to":"e4"}"""))
        assertEquals(normal("e7e8q"), ChessJson.decodeMove("""{"type":"normal","from":"e7","to":"e8","promotion":"QUEEN"}"""))
        assertEquals(normal("e2e4"), ChessJson.decodeMove("""{"type":"normal","from":"e2","to":"e4","promotion":null}"""))
        assertEquals(normal("e2e4"), ChessJson.decodeMove("""{"from":"e2","to":"e4","type":"normal","extra":1}"""))
        assertEquals(split("g1", "f3", "h3"), ChessJson.decodeMove("""{"type":"split","from":"g1","first":"f3","second":"h3"}"""))
        assertEquals(split("e2", "e4", "e2"), ChessJson.decodeMove("""{"type":"split","from":"e2","first":"e4","second":"e2"}"""))
        assertEquals(GameMove.Observe(sq("e4")), ChessJson.decodeMove("""{"type":"observe","square":"e4"}"""))
        assertEquals(GameMove.Observe(sq("e4")), ChessJson.decodeMove("""{"type":"observe","square":"e4","outcome":null}"""))
        assertEquals(observe("e4", null), ChessJson.decodeMove("""{"type":"observe","square":"e4","outcome":{"piece":null}}"""))
        assertEquals(
            observe("e4", piece('P')),
            ChessJson.decodeMove("""{"type":"observe","square":"e4","outcome":{"piece":{"color":"WHITE","type":"PAWN"}}}"""),
        )
    }

    @Test
    fun movesEncodeToTheDocumentedShapes() {
        assertEquals("""{"type":"normal","from":"e7","to":"e8","promotion":"QUEEN"}""", ChessJson.encodeMove(normal("e7e8q")))
        assertEquals("""{"type":"split","from":"g1","first":"f3","second":"h3"}""", ChessJson.encodeMove(split("g1", "f3", "h3")))
        assertEquals(
            """{"type":"split","from":"a7","first":"a8","second":"a7","promotion":"KNIGHT"}""",
            ChessJson.encodeMove(split("a7", "a8", "a7", PieceType.KNIGHT)),
        )
        assertEquals(
            """{"type":"observe","square":"e4","outcome":{"piece":{"color":"WHITE","type":"PAWN"}}}""",
            ChessJson.encodeMove(observe("e4", piece('P'))),
        )
        assertEquals("""[{"type":"normal","from":"e2","to":"e4"}]""", ChessJson.encodeMoves(listOf(normal("e2e4"))))
    }

    @Test
    fun malformedMovesAreRejected() {
        assertFailsWith<Exception> { ChessJson.decodeMove("""{"type":"normal","from":"e9","to":"e4"}""") }
        assertFailsWith<Exception> { ChessJson.decodeMove("""{"type":"normal","from":"e2"}""") }
        assertFailsWith<Exception> { ChessJson.decodeMove("""{"type":"teleport","from":"e2","to":"e4"}""") }
        assertFailsWith<Exception> { ChessJson.decodeMove("""{"from":"e2","to":"e4"}""") }
        assertFailsWith<Exception> { ChessJson.decodeMove("""{"type":"normal","from":"e2","to":"e4","promotion":"queen"}""") }
    }

    @Test
    fun statusEncodesEveryReason() {
        for (reason in EndReason.entries) {
            val white = parse(ChessJson.encodeStatus(GameStatus.Finished(Color.WHITE, reason)))
            assertEquals(setOf("type", "winner", "reason"), white.keys)
            assertEquals(reason.name, white["reason"]!!.jsonPrimitive.content)
            val draw = parse(ChessJson.encodeStatus(GameStatus.Finished(null, reason)))
            assertEquals(JsonNull, draw["winner"])
        }
        assertEquals(setOf("type"), parse(ChessJson.encodeStatus(GameStatus.Ongoing)).keys)
    }

    @Test
    fun observationAndCellEntryAlwaysSpellOutNullPiece() {
        // encodeDefaults = false must not drop "piece": null, since neither property has a default.
        assertEquals("""{"piece":null}""", ChessJson.json.encodeToString(Observation.serializer(), Observation(null)))
        assertEquals("""{"piece":null,"probability":0.5}""", ChessJson.json.encodeToString(CellEntry.serializer(), CellEntry(null, 0.5)))
        assertNull(ChessJson.json.decodeFromString(Observation.serializer(), """{"piece":null}""").piece)
    }

    @Test
    fun historyRoundTripsThroughJsonForEveryMoveKind() {
        val game = Game(GameKind.QUANTUM)
        game.apply(split("e2", "e3", "e4"))
        game.apply(normal("a7a6"))
        game.apply(observe("e4", piece('P')))
        game.apply(normal("a6a5"))
        val json = ChessJson.encodeMoves(game.history)
        val replayed = Game.replay(GameKind.QUANTUM, ChessJson.decodeMoves(json))
        assertEquals(game.history, replayed.history)
        assertEquals(ChessJson.encodeView(game.view()), ChessJson.encodeView(replayed.view()))
        assertTrue(json.contains(""""outcome":{"piece":{"color":"WHITE","type":"PAWN"}}"""))
    }
}
