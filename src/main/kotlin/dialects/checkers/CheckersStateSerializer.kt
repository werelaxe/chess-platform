package dialects.checkers

import core.Coordinate
import dialects.StateSerializer


object CheckersStateSerializer: StateSerializer<CheckersFigure, CheckersState, CheckersRules, CheckersGame>() {
    override fun serialize(state: CheckersState): List<List<Int?>> {
        return MutableList(state.height) { y ->
            MutableList(state.width) { x ->
                state[Coordinate.of(x, y)]?.let { fig ->
                    getFigureId(fig)
                }
            }
        }
    }
}
