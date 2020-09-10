package dialects.simple

import core.Coordinate
import core.Figure
import core.State


class SimpleState
    : State<SimpleFigure, SimplePlayer>(SimplePlayer.FIRST) {
    private var board: MutableList<Figure<SimpleFigure, SimplePlayer>?>

    init {
        board = MutableList(10) { index ->
            when {
                index <= 1 -> Figure(SimplePlayer.FIRST, SimpleFigure.ONE)
                index >= 8 -> Figure(SimplePlayer.SECOND, SimpleFigure.ONE)
                else -> null
            }
        }
    }

    override fun get(coord: Coordinate): Figure<SimpleFigure, SimplePlayer>? {
        return board[coord.nums[0]]
    }

    override fun set(coord: Coordinate, figure: Figure<SimpleFigure, SimplePlayer>?) {
        board[coord.nums[0]] = figure
    }

    override fun toString(): String {
        return board.joinToString("") { figure ->
            when (figure?.owner) {
                SimplePlayer.FIRST -> "0"
                SimplePlayer.SECOND -> "1"
                else -> "_"
            }
        } + "\n" +
                "Current player: $currentPlayer\n"
    }
}
