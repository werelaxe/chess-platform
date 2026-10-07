package ru.werelaxe.chess.core

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** The JSON configuration shared by the server, the clients and the database. */
object ChessJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        classDiscriminator = "type"
    }

    fun encodeMove(move: GameMove): String = json.encodeToString(GameMove.serializer(), move)

    fun decodeMove(text: String): GameMove = json.decodeFromString(GameMove.serializer(), text)

    fun encodeMoves(moves: List<GameMove>): String = json.encodeToString(ListSerializer(GameMove.serializer()), moves)

    fun decodeMoves(text: String): List<GameMove> = json.decodeFromString(ListSerializer(GameMove.serializer()), text)

    fun encodeStatus(status: GameStatus): String = json.encodeToString(GameStatus.serializer(), status)

    fun encodeView(view: BoardView): String = json.encodeToString(BoardView.serializer(), view)
}
