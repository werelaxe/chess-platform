package dialects.checkers

import core.Figure

enum class CheckersFigureType {
    MEN,
    KNIGHT
}


data class CheckersFigure (
        override val owner: Int,
        val figureType: CheckersFigureType
) : Figure {
    override val type: Int
        get() = figureType.ordinal
}
