package core

interface Rules<FigureType: Figure, StateType: State<FigureType>> {
    fun canMove(state: StateType, from: Coordinate, to: Coordinate): Boolean
    fun nextPlayer(state: StateType, from: Coordinate, to: Coordinate): Int
    fun isTerminateState(state: StateType): Boolean
    fun winners(state: StateType): Result
}
