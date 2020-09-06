package app

import core.*
import simple.SimpleRules
import simple.SimpleState


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
    val game = Game(SimpleState(), SimpleRules())
    ConsoleSimpleGameRunner(game).run()
}
