package dialects

import core.Game
import core.Rules
import core.State
import dialects.simple.*

object GameCollection {
    private val m = mutableMapOf<GameKind, () -> Game<*, *, *, *>>()

    fun create(kind: GameKind): Game<*, *, *, *> {
        m[kind]?.let {
            return it()
        }
        throw NoSuchElementException("No such constructor for type $kind")
    }

    fun <Figure, Player, StateType : State<Figure, Player>, RulesType : Rules<StateType, Player>>
            register(kind: GameKind, initGame: () -> Game<Figure, Player, StateType, RulesType>) {
        m[kind] = initGame
    }

    init {
        register(GameKind.SIMPLE, ::createSimpleGame)
    }
}
