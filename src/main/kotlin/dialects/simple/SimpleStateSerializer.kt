package dialects.simple

import core.Coordinate
import dialects.StateSerializer

object SimpleStateSerializer: StateSerializer<SimpleFigure, SimpleState, SimpleGame> {
    private val figure2Id = mutableMapOf<SimpleFigure, Int>()
    private val id2Figure = mutableMapOf<Int, SimpleFigure>()

    private fun getFigureId(figure: SimpleFigure): Int {
        if (figure !in figure2Id) {
            figure2Id[figure] = figure2Id.size
            id2Figure[figure2Id[figure]!!] = figure
        }
        return figure2Id[figure]!!
    }

    override fun serialize(state: SimpleState): List<List<Int?>> {
        return listOf(MutableList(SimpleState.SIZE) { coord ->
            state[Coordinate.of(coord)]?.let { figure ->
                getFigureId(figure)
            }
        })
    }
}
