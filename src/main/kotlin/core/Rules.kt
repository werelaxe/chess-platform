package core

interface Rules<FigureType: Figure, StateType: State<FigureType>> {
    fun nextPlayer(state: StateType, from: Coordinate, to: Coordinate): Int
    fun isTerminateState(state: StateType): Boolean
    fun winners(state: StateType): Result
    fun possibleSteps(state: StateType, from: Coordinate): List<Coordinate>

    fun move(state: StateType, from: Coordinate, to: Coordinate) {
        state.move(from, to)
    }

    fun canMove(state: StateType, from: Coordinate, to: Coordinate, info: AdditionalStepInfo? = null) = to in possibleSteps(state, from)
    fun isCurrentPlayerStep(state: StateType, figure: FigureType): Boolean
}
