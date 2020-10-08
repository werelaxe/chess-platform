package dialects

import core.Figure
import core.Game
import core.State
import dialects.checkers.CheckersGame
import dialects.simple.*

typealias GameType = Game<out Figure, out State<out Figure>>


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
    }
}
