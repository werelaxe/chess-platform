package app

import core.*
import dialects.GameCollection
import dialects.GameKind
import dialects.simple.SimpleFigure

//
//class ConsoleSimpleGameRunner (
//    private val game: Game<*>
//) {
//    fun run() {
//        println(game)
//        while (!game.isOver()) {
//            val line = readLine() ?: return
//            val (from, to) = line.split(" ").map { it.toInt() }
//            try {
//                game.step(Coordinate.of(from), Coordinate.of(to))
//            } catch (e: Exception) {
//                println("Error: $e")
//            }
//            println(game)
//        }
//        println(game.result())
//    }
//}


fun main() {
    println("!")
}
