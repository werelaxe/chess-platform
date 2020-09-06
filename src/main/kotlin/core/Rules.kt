package core

interface Rules<StateType, Player> {
    fun canMove(state: StateType, from: Coordinate, to: Coordinate): Boolean
    fun nextPlayer(state: StateType, from: Coordinate, to: Coordinate): Player
    fun isTerminateState(state: StateType): Boolean
    fun winners(state: StateType): Result<Player>
}
