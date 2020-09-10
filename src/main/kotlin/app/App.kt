package app

import core.*
import dialects.GameCollection
import dialects.GameKind
import dialects.simple.SimpleRules
import dialects.simple.SimpleState


class ConsoleSimpleGameRunner <Figure, Player, StateType: State<Figure, Player>, RulesType: Rules<StateType, Player>>(
    private val game: Game<Figure, Player, StateType, RulesType>
) {
    fun run() {
        println(game)
        while (!game.isOver()) {
            val line = readLine() ?: return
            val (from, to) = line.split(" ").map { it.toInt() }
            try {
                game.step(Coordinate.of(from), Coordinate.of(to))
            } catch (e: Exception) {
                println("Error: $e")
            }
            println(game)
        }
        println(game.result())
    }
}


fun main() {
    val game = GameCollection.create(GameKind.SIMPLE)
    ConsoleSimpleGameRunner(game).run()
}
