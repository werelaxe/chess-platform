package dialects.checkers

import core.Coordinate
import core.State

class CheckersState(
    val width: Int,
    val height: Int
): State<CheckersFigure>(CheckersPlayer.BLACK) {
    var isOver = false
    var blackCount = 0
    var whiteCount = 0

    private val board: MutableList<MutableList<CheckersFigure?>> = MutableList(height) { y ->
        MutableList(width) { x ->
            if ((x + y) % 2 == 0 && (y <= 2 || y >= height - 3)) {
                if (y <= 2) {
                    blackCount++
                    CheckersFigure(CheckersPlayer.BLACK, CheckersFigureType.MEN)
                } else {
                    whiteCount++
                    CheckersFigure(CheckersPlayer.WHITE, CheckersFigureType.MEN)
                }
            } else null
        }
    }

    override fun getEl(coord: Coordinate): CheckersFigure? {
        return board[coord.nums[1]][coord.nums[0]]
    }

    override fun setEl(coord: Coordinate, figure: CheckersFigure?) {
        board[coord.nums[1]][coord.nums[0]] = figure
    }

    override fun contains(coord: Coordinate) = coord.nums[0] in 0 until width && coord.nums[1] in 0 until height
}
