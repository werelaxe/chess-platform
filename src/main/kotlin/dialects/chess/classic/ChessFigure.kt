package dialects.chess.classic

import core.Figure
import dialects.checkers.CheckersFigureType


enum class ChessFigureType {
    PAWN,
    BISHOP,
    KNIGHT,
    ROOK,
    QUEEN,
    KING
}


data class ChessFigure (
    override val owner: Int,
    val figureType: ChessFigureType,
    val canCastling: Boolean = false
) : Figure {
    override val type: Int
        get() = figureType.ordinal
}