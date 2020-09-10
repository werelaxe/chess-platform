package dialects.simple

import core.Coordinate
import core.Result
import core.Rules
import kotlin.math.abs


class SimpleRules: Rules<SimpleState, SimplePlayer> {
    override fun canMove(state: SimpleState, from: Coordinate, to: Coordinate): Boolean {
        val fromFig = state[from]
        val toFig = state[to]
        if (state.currentPlayer != fromFig?.owner || toFig != null
                || to.nums.single() < 0 || to.nums.single() > 9) {
            return false
        }
        return when (abs(to.nums.single() - from.nums.single())) {
            1 -> true
            2 -> state[(to + from) / 2] != null
            else -> false
        }
    }

    override fun isTerminateState(state: SimpleState): Boolean {
        return state[Coordinate.of(0)]?.owner == SimplePlayer.SECOND &&
                state[Coordinate.of(1)]?.owner == SimplePlayer.SECOND &&
                state[Coordinate.of(8)]?.owner == SimplePlayer.FIRST &&
                state[Coordinate.of(9)]?.owner == SimplePlayer.FIRST
    }

    override fun winners(state: SimpleState) =
            if (isTerminateState(state)) Result(true, setOf(SimplePlayer.FIRST, SimplePlayer.SECOND), emptySet())
            else Result(false, emptySet(), emptySet())

    override fun nextPlayer(state: SimpleState, from: Coordinate, to: Coordinate): SimplePlayer =
            if (state.currentPlayer == SimplePlayer.FIRST) SimplePlayer.SECOND else SimplePlayer.FIRST
}
