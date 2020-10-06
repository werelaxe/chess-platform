package dialects.simple

import core.Game
import dialects.GameKind

fun createSimpleGame() = Game(GameKind.SIMPLE, SimpleState(), SimpleRules())

class SimpleGame: Game<SimpleFigure, SimpleState>(GameKind.SIMPLE, SimpleState(), SimpleRules())
