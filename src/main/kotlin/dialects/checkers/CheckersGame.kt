package dialects.checkers

import core.Coordinate
import core.Game
import dialects.GameKind
import dialects.simple.SimpleGame

class CheckersGame: Game<CheckersFigure, CheckersState>(
    GameKind.CHECKERS,
    CheckersState(8, 8),
    CheckersRules()
) {
    override fun step(from: Coordinate, to: Coordinate) {
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
        state.currentPlayer = rules.nextPlayer(state, from, to)
    }

    companion object {
        fun createCheckersGame() = CheckersGame()
    }
}
