package dialects.checkers

import core.Coordinate
import core.Result
import core.Rules

class CheckersRules: Rules<CheckersFigure, CheckersState> {
    override fun canMove(state: CheckersState, from: Coordinate, to: Coordinate): Boolean {
        if (state.isOver) {
            return false
        }
        if (to !in state) {
            return false
        }
        state[from]?.let { fromFig ->
            if (fromFig.owner != state.currentPlayer) {
                return false
            }
            if (from.nums[0] != to.nums[0]) {
                return false
            }
            return to.nums[1] - from.nums[1] == 1
        }
        return false
    }

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
}
 