package dialects.simple

import core.*
import kotlin.math.abs


class SimpleRules: Rules<SimpleFigure, SimpleState> {
    override fun isTerminateState(state: SimpleState): Boolean {
        return state.getEl(Coordinate.of(0))?.owner == SimplePlayer.SECOND &&
                state.getEl(Coordinate.of(1))?.owner == SimplePlayer.SECOND &&
                state.getEl(Coordinate.of(8))?.owner == SimplePlayer.FIRST &&
                state.getEl(Coordinate.of(9))?.owner == SimplePlayer.FIRST
    }

    override fun winners(state: SimpleState) =
            if (isTerminateState(state)) Result(true, setOf(SimplePlayer.FIRST, SimplePlayer.SECOND), emptySet())
            else Result(false, emptySet(), emptySet())

    override fun nextPlayer(state: SimpleState, from: Coordinate, to: Coordinate): Int =
            if (state.currentPlayer == SimplePlayer.FIRST) SimplePlayer.SECOND else SimplePlayer.FIRST

    override fun possibleSteps(state: SimpleState, from: Coordinate): List<Coordinate> {
        state[from]?.let { fromFig ->
            if (fromFig.owner != state.currentPlayer) {
                return emptyList()
            }
            if ((state.currentPlayer == SimplePlayer.FIRST && from.single() == SimpleState.SIZE - 1) ||
                    (state.currentPlayer == SimplePlayer.SECOND && from.single() == 0)) {
                return emptyList()
            }
            val res = mutableListOf<Coordinate>()
            val playerCoef = if (state.currentPlayer == SimplePlayer.FIRST) 1 else - 1
            val next = from + Coordinate.of(1) * playerCoef
            val nextNext = from + Coordinate.of(2) * playerCoef
            if (state[next] == null) {
                res.add(next)
            }
            if (nextNext in state && state[nextNext] == null && state[next] != null) {
                res.add(nextNext)
            }
            return res
        }
        return emptyList()
    }
}
