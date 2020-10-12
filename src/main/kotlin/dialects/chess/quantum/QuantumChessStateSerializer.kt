package dialects.chess.quantum

import core.Coordinate
import dialects.StateSerializer


object QuantumChessStateSerializer: StateSerializer<QuantumChessFigure, QuantumChessState, QuantumChessRules, QuantumChessGame>() {
    override fun serialize(state: QuantumChessState): List<List<Int?>> {
        return MutableList(state.height) { y ->
            MutableList(state.width) { x ->
                state[Coordinate.of(x, y)]?.let { fig ->
                    getFigureId(fig)
                }
            }
        }
    }
}
