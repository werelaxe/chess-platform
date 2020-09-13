package core

interface Rules {
    fun canMove(state: State, from: Coordinate, to: Coordinate): Boolean
    fun nextPlayer(state: State, from: Coordinate, to: Coordinate): Player
    fun isTerminateState(state: State): Boolean
    fun winners(state: State): Result
}
