package dialects.simple

import core.Game

fun createSimpleGame() = Game(SimpleState(), SimpleRules())
