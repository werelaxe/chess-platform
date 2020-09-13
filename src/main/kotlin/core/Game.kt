package core


class Game (
    private val state: State,
    private val rules: Rules
) {
    fun isOver() = rules.isTerminateState(state)

    val roState = state.ro()

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

    fun result(): Result {
        return rules.winners(state)
    }

    override fun toString(): String {
        return state.toString() + "Is over: ${isOver()}"
    }
}
