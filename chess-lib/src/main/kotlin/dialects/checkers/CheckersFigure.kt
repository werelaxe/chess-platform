package dialects.checkers

import core.Figure

enum class CheckersFigureType {
    MEN,
    KNIGHT
}


data class CheckersFigure (
        val owner: Int,
        val figureType: CheckersFigureType
) : Figure {
    val type: Int
        get() = figureType.ordinal
}
