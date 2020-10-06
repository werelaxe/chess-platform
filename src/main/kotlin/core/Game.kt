package core

import dialects.GameKind


open class Game <FigureType: Figure, StateType: State<FigureType>> (
    val kind: GameKind,
    val state: StateType,
    private val rules: Rules<FigureType, StateType>
) {
    fun isOver() = rules.isTerminateState(state)

    fun step(from: Coordinate, to: Coordinate) {
        if (isOver()) {
            throw Exception("Game is over")
        }

        if (!rules.canMove(state, from, to)) {
            throw Exception("Can not move")
        }

        state.move(from, to)
        state.currentPlayer = rules.nextPlayer(state, from, to)
    }

    fun canMove(from: Coordinate, to: Coordinate) = rules.canMove(state, from, to)

    fun result(): Result {
        return rules.winners(state)
    }

    override fun toString(): String {
        return state.toString() + "Is over: ${isOver()}"
    }
}
