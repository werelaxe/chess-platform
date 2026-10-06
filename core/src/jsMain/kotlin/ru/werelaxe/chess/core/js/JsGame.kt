@file:OptIn(ExperimentalJsExport::class)

package ru.werelaxe.chess.core.js

import ru.werelaxe.chess.core.ChessJson
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.QuantumState
import ru.werelaxe.chess.core.Square

/**
 * JavaScript facade over [Game]. Moves, views and statuses cross the boundary as JSON strings
 * in exactly the format used by the HTTP API, so the web client needs a single set of types.
 */
@JsExport
class JsGame(kind: String) {
    // Kotlin/JS exports only the primary constructor, so wrapping an existing game has to go through
    // a secondary one: it delegates to the primary and then installs the game it was given.
    private var game: Game = Game(GameKind.valueOf(kind))

    internal constructor(game: Game) : this(game.kind.name) {
        this.game = game
    }

    val kind: String
        get() = game.kind.name

    fun sideToMove(): String = game.sideToMove.name

    fun moveCount(): Int = game.moveCount

    fun universeCount(): Int = (game.state as? QuantumState)?.universeCount ?: 1

    fun isOver(): Boolean = game.status().isOver

    fun statusJson(): String = ChessJson.encodeStatus(game.status())

    fun viewJson(): String = ChessJson.encodeView(game.view())

    fun historyJson(): String = ChessJson.encodeMoves(game.history)

    fun legalTargets(from: Int): IntArray = game.legalTargets(Square(from)).map { it.index }.toIntArray()

    fun splitSecondTargets(from: Int, first: Int): IntArray =
        game.splitSecondTargets(Square(from), Square(first)).map { it.index }.toIntArray()

    fun requiresPromotion(from: Int, to: Int): Boolean = game.requiresPromotion(Square(from), Square(to))

    fun canObserve(square: Int): Boolean = game.canObserve(Square(square))

    fun isLegalJson(moveJson: String): Boolean =
        try {
            game.isLegal(ChessJson.decodeMove(moveJson))
        } catch (e: Throwable) {
            false
        }

    /** Applies a move given as JSON; throws an Error with a message when it is illegal. */
    fun applyJson(moveJson: String) {
        game.apply(ChessJson.decodeMove(moveJson))
    }
}

/** Builds a game by replaying a JSON array of moves; throws when the history is illegal. */
@JsExport
fun replayGame(kind: String, movesJson: String): JsGame =
    JsGame(Game.replay(GameKind.valueOf(kind), ChessJson.decodeMoves(movesJson)))

@JsExport
fun squareName(index: Int): String = Square(index).name

@JsExport
fun squareIndex(name: String): Int = Square.parse(name).index
