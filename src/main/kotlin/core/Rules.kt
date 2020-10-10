package core

interface Rules<FigureType: Figure, StateType: State<FigureType>> {
    fun nextPlayer(state: StateType, from: Coordinate, to: Coordinate): Int
    fun isTerminateState(state: StateType): Boolean
    fun winners(state: StateType): Result
    fun possibleSteps(state: StateType, from: Coordinate): List<Coordinate>

    fun canMove(state: StateType, from: Coordinate, to: Coordinate) = to in possibleSteps(state, from)
    fun isCurrentPlayerStep(state: StateType, figure: FigureType) = figure.owner == state.currentPlayer
}
