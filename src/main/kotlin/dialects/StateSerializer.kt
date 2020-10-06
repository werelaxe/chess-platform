package dialects

import core.Figure
import core.Game
import core.State
import dialects.simple.SimpleState
import dialects.simple.SimpleStateSerializer

interface StateSerializer <FigureType: Figure, StateType: State<FigureType>, GameType: Game<FigureType, StateType>> {
    fun serialize(state: StateType): List<List<Int?>>

    companion object {
        fun <FigureType: Figure, StateType: State<FigureType>, GameType: Game<FigureType, StateType>> serialize(game: GameType): List<List<Int?>> {
            if (game.state is SimpleState) {
                return SimpleStateSerializer.serialize(game.state)
            } else {
                throw Exception("Unknown state type: ${game.state.javaClass}")
            }
        }
    }
}
