package dialects.checkers

import core.Coordinate
import core.Result
import core.Rules


class CheckersRules: Rules<CheckersFigure, CheckersState> {
    override fun nextPlayer(state: CheckersState, from: Coordinate, to: Coordinate): Int {
        return if (state.currentPlayer == CheckersPlayer.BLACK) CheckersPlayer.WHITE else CheckersPlayer.BLACK
    }

    override fun isTerminateState(state: CheckersState): Boolean {
        return state.isOver
    }

    override fun winners(state: CheckersState): Result {
        if (!isTerminateState(state)) {
            return Result(false, emptySet(), emptySet())
        }

        if (state.whiteCount == 0) {
            return Result(true, setOf(CheckersPlayer.BLACK), setOf(CheckersPlayer.WHITE))
        }
        return Result(true, setOf(CheckersPlayer.WHITE), setOf(CheckersPlayer.BLACK))
    }

    override fun possibleSteps(state: CheckersState, from: Coordinate): List<Coordinate> {
        state[from]?.let { fromFig ->
            if (!isCurrentPlayerStep(state, fromFig)) {
                return emptyList()
            }
            val res = mutableListOf<Coordinate>()
            val playerCoef = if (state.currentPlayer == CheckersPlayer.BLACK) 1 else - 1
            val next = from + Coordinate.of(0, 1) * playerCoef
            val nextNext = from + Coordinate.of(0, 2) * playerCoef
            if (next in state) {
                res.add(next)
            }
            if (nextNext in state && state[next] != null) {
                res.add(nextNext)
            }
            return res
        }
        return emptyList()
    }
}
