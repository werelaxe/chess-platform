package core


class Game<Figure, Player, StateType: State<Figure, Player>, RulesType: Rules<StateType, Player>> (
    private val state: StateType,
    private val rules: RulesType
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

    fun result(): Result<Player> {
        return rules.winners(state)
    }

    override fun toString(): String {
        return state.toString() + "Is over: ${isOver()}"
    }
}
