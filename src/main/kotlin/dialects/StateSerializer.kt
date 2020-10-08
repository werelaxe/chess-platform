package dialects

import core.Figure
import core.Game
import core.State
import dialects.checkers.CheckersFigure
import dialects.checkers.CheckersState
import dialects.checkers.CheckersStateSerializer
import dialects.simple.SimpleState
import dialects.simple.SimpleStateSerializer

abstract class StateSerializer <FigureType: Figure, StateType: State<FigureType>, GameType: Game<FigureType, StateType>> {
    private val figure2Id = mutableMapOf<FigureType, Int>()
    private val id2Figure = mutableMapOf<Int, FigureType>()

    protected fun getFigureId(figure: FigureType): Int {
        if (figure !in figure2Id) {
            figure2Id[figure] = figure2Id.size
            id2Figure[figure2Id[figure]!!] = figure
        }
        return figure2Id[figure]!!
    }

    abstract fun serialize(state: StateType): List<List<Int?>>

    companion object {
        fun <FigureType: Figure, StateType: State<FigureType>, GameType: Game<FigureType, StateType>> serialize(game: GameType): List<List<Int?>> {
            return when (game.state) {
                is SimpleState -> {
                    SimpleStateSerializer.serialize(game.state)
                }
                is CheckersState -> {
                    CheckersStateSerializer.serialize(game.state)
                }
                else -> throw Exception("Unknown state type: ${game.state.javaClass}")
            }
        }
    }
}
