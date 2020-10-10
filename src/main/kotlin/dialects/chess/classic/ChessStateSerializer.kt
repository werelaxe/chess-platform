package dialects.chess.classic

import core.Coordinate
import dialects.StateSerializer


object ChessStateSerializer: StateSerializer<ChessFigure, ChessState, ChessGame>() {
    override fun serialize(state: ChessState): List<List<Int?>> {
        return MutableList(state.height) { y ->
            MutableList(state.width) { x ->
                state[Coordinate.of(x, y)]?.let { fig ->
                    getFigureId(fig)
                }
            }
        }
    }
}
