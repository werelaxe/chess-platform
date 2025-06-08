package dialects.chess.quantum

import core.Figure
import dialects.chess.classic.ChessFigure
import dialects.chess.classic.ChessFigureType


data class QuantumFigurePair(
    val probability: Double,
    val figure: ChessFigure
)


data class QuantumChessFigure(
    val figures: List<QuantumFigurePair>
): Figure