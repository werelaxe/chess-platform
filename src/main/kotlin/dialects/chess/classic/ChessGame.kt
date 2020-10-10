package dialects.chess.classic

import core.Coordinate
import core.Game
import dialects.GameKind

class ChessGame: Game<ChessFigure, ChessState>(
    GameKind.CLASSIC_CHESS,
    ChessState(),
    ChessRules()
) {
    override fun step(from: Coordinate, to: Coordinate) {
        preStepCheck(from, to)
        state[to]?.let { fig ->
            if (fig.owner == ChessPlayer.BLACK) {
                state.blackCount--
            }
            if (fig.owner == ChessPlayer.WHITE) {
                state.whiteCount--
            }
        }
        state.move(from, to)

        // TODO: process pawn transformation here

        state.currentPlayer = rules.nextPlayer(state, from, to)
    }

    companion object {
        fun createChessGame() = ChessGame()
    }
}
