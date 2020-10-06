package dialects.simple

import core.Figure


enum class SimpleFigureType {
    ONE
}


data class SimpleFigure (
    override val owner: Int,
    val figureType: SimpleFigureType
) : Figure {
    override val type: Int
        get() = figureType.ordinal
}
