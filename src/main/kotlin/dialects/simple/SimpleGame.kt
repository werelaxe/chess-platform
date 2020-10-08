package dialects.simple

import core.Game
import dialects.GameKind


class SimpleGame: Game<SimpleFigure, SimpleState>(GameKind.SIMPLE, SimpleState(), SimpleRules()) {
    companion object {
        fun createSimpleGame() = Game(GameKind.SIMPLE, SimpleState(), SimpleRules())
    }
}
