package dialects

import core.Figure
import core.Game
import core.Rules
import core.State
import dialects.checkers.CheckersGame
import dialects.chess.classic.ChessGame
import dialects.chess.quantum.QuantumChessGame
import dialects.simple.*

typealias GameType = Game<out Figure, out State<out Figure>, out Rules<out Figure, out State<out Figure>>>


object GameCollection {
    private val kind2Init = mutableMapOf<GameKind, () -> GameType>()

    fun create(kind: GameKind): GameType {
        kind2Init[kind]?.let {
            return it()
        }
        throw NoSuchElementException("No such constructor for kind $kind")
    }

    private fun register(kind: GameKind, initGame: () -> GameType) {
        kind2Init[kind] = initGame
    }

    init {
        register(GameKind.SIMPLE, SimpleGame.Companion::createSimpleGame)
        register(GameKind.CHECKERS, CheckersGame.Companion::createCheckersGame)
        register(GameKind.CLASSIC_CHESS, ChessGame.Companion::createChessGame)
        register(GameKind.QUANTUM_CHESS, QuantumChessGame.Companion::createQuantumChessGame)
    }
}
