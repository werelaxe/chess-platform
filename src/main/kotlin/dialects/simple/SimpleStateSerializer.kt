package dialects.simple

import core.Coordinate
import dialects.StateSerializer

object SimpleStateSerializer: StateSerializer<SimpleFigure, SimpleState, SimpleGame>() {
    override fun serialize(state: SimpleState): List<List<Int?>> {
        return listOf(MutableList(SimpleState.SIZE) { coord ->
            state.getEl(Coordinate.of(coord))?.let { figure ->
                getFigureId(figure)
            }
        })
    }
}
