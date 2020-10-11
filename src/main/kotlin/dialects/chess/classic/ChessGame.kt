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
        state.move(from, to)
        processPawnTransformation(to)
        state.currentPlayer = rules.nextPlayer(state, from, to)
    }

    private fun processPawnTransformation(to: Coordinate) {
        if (state[to]?.figureType != ChessFigureType.PAWN) {
            return
        }
        if (state.currentPlayer == ChessPlayer.WHITE && to.y() == 0) {
            state[to] = ChessFigure(ChessPlayer.WHITE, ChessFigureType.QUEEN)
        } else if (state.currentPlayer == ChessPlayer.BLACK && to.y() == state.height - 1) {
            state[to] = ChessFigure(ChessPlayer.BLACK, ChessFigureType.QUEEN)
        }
    }

    companion object {
        fun createChessGame() = ChessGame()
    }
}
