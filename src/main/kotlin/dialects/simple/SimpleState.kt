package dialects.simple

import core.Coordinate
import core.Figure
import core.State


class SimpleState
    : State<SimpleFigure>(SimplePlayer.FIRST) {
    private var board: MutableList<SimpleFigure?>

    init {
        board = MutableList(SIZE) { index ->
            when {
                index <= 1 -> SimpleFigure(SimplePlayer.FIRST, SimpleFigureType.ONE)
                index >= 8 -> SimpleFigure(SimplePlayer.SECOND, SimpleFigureType.ONE)
                else -> null
            }
        }
    }

    override fun get(coord: Coordinate): SimpleFigure? {
        return board[coord.nums[0]]
    }

    override fun set(coord: Coordinate, figure: SimpleFigure?) {
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

    override fun full(): Map<Coordinate, SimpleFigure?> {
        return (0 until SIZE).map {
            Coordinate.of(it) to board[it]
        }.toMap()
    }

    companion object {
        const val SIZE = 10
    }
}
