package dialects.chess.classic

import core.AdditionalStepInfo
import core.Coordinate
import core.Game
import dialects.GameKind

class ChessGame: Game<ChessFigure, ChessState, ChessRules>(
    GameKind.CLASSIC_CHESS,
    ChessState(),
    ChessRules()
) {
    companion object {
        fun createChessGame() = ChessGame()
    }
}
