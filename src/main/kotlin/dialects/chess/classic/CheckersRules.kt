package dialects.chess.classic

import core.Coordinate
import core.Result
import core.Rules


class ChessRules: Rules<ChessFigure, ChessState> {
    override fun nextPlayer(state: ChessState, from: Coordinate, to: Coordinate): Int {
        return if (state.currentPlayer == ChessPlayer.BLACK) ChessPlayer.WHITE else ChessPlayer.BLACK
    }

    override fun isTerminateState(state: ChessState): Boolean {
        return state.isOver
    }

    override fun winners(state: ChessState): Result {
        if (!isTerminateState(state)) {
            return Result(false, emptySet(), emptySet())
        }

        if (state.whiteCount == 0) {
            return Result(true, setOf(ChessPlayer.BLACK), setOf(ChessPlayer.WHITE))
        }
        return Result(true, setOf(ChessPlayer.WHITE), setOf(ChessPlayer.BLACK))
    }

    override fun possibleSteps(state: ChessState, from: Coordinate): List<Coordinate> {
        return emptyList()
    }
}
