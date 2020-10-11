package core

import dialects.GameKind


open class Game <FigureType: Figure, StateType: State<FigureType>, RulesType: Rules<FigureType, StateType>> (
    val kind: GameKind,
    val state: StateType,
    protected val rules: RulesType
) {
    fun isOver() = rules.isTerminateState(state)

    open fun preStepCheck(from: Coordinate, to: Coordinate) {
        if (isOver()) {
            throw Exception("Game is over")
        }

        if (!rules.canMove(state, from, to)) {
            throw Exception("Can not move")
        }
    }

    open fun step(from: Coordinate, to: Coordinate) {
        preStepCheck(from, to)
        state.move(from, to)
        state.currentPlayer = rules.nextPlayer(state, from, to)
    }

    fun canMove(from: Coordinate, to: Coordinate) = rules.canMove(state, from, to)

    fun possibleSteps(from: Coordinate) = rules.possibleSteps(state, from)

    fun result(): Result {
        return rules.winners(state)
    }

    override fun toString(): String {
        return state.toString() + "Is over: ${isOver()}"
    }
}
