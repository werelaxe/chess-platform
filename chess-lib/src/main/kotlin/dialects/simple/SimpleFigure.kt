package dialects.simple

import core.Figure


enum class SimpleFigureType {
    ONE
}


data class SimpleFigure (
    val owner: Int,
    val figureType: SimpleFigureType
) : Figure {
    val type: Int
        get() = figureType.ordinal
}
