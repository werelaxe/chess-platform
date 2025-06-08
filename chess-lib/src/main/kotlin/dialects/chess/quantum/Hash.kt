package dialects.chess.quantum

import core.Coordinate
import dialects.chess.classic.ChessFigure

fun cellHash(coordinate: Coordinate, figure: ChessFigure?) = figure?.let {
    (coordinate.x() * 31 + coordinate.y() * 13) * (it.owner * 7 + it.type * 19)
} ?: 0

const val MAX_HASH_VAL = Int.MAX_VALUE
