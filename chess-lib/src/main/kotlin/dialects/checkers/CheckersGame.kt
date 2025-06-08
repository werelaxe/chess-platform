package dialects.checkers

import core.AdditionalStepInfo
import core.Coordinate
import core.Game
import dialects.GameKind

class CheckersGame: Game<CheckersFigure, CheckersState, CheckersRules>(
    GameKind.CHECKERS,
    CheckersState(8, 8),
    CheckersRules()
) {
    override fun step(from: Coordinate, to: Coordinate, additionalStepInfo: AdditionalStepInfo?) {
        preStepCheck(from, to)
        state[to]?.let { fig ->
            if (fig.owner == CheckersPlayer.BLACK) {
                state.blackCount--
            }
            if (fig.owner == CheckersPlayer.WHITE) {
                state.whiteCount--
            }
        }
        state.move(from, to)

        if (state.currentPlayer == CheckersPlayer.BLACK && to.nums[1] == state.height - 1) {
            state[to]?.let { fig ->
                if (fig.figureType == CheckersFigureType.MEN) {
                    state[to] = CheckersFigure(fig.owner, CheckersFigureType.KNIGHT)
                }
            }
        } else if (state.currentPlayer == CheckersPlayer.WHITE && to.nums[1] == 0) {
            state[to]?.let { fig ->
                if (fig.figureType == CheckersFigureType.MEN) {
                    state[to] = CheckersFigure(fig.owner, CheckersFigureType.KNIGHT)
                }
            }
        }
        state.currentPlayer = rules.nextPlayer(state, from, to)
    }

    companion object {
        fun createCheckersGame() = CheckersGame()
    }
}
