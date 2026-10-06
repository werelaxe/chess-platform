package ru.werelaxe.chess.core

import kotlinx.serialization.Serializable

/** One possible content of a square with its probability. [piece] is null for "empty". */
@Serializable
data class CellEntry(val piece: Piece?, val probability: Double)

/**
 * What a square may contain. [entries] is sorted by decreasing probability and omits
 * the "empty" entry when the square is certainly empty, so an empty list means an empty square.
 */
@Serializable
data class CellView(val square: Square, val entries: List<CellEntry>) {
    val isEmpty: Boolean
        get() = entries.isEmpty()

    val isClassical: Boolean
        get() = entries.size <= 1 && entries.all { it.probability >= 1.0 }
}

/**
 * A variant-independent rendering of a game state. Classic chess produces single-entry cells
 * with probability 1; quantum chess produces distributions.
 */
@Serializable
data class BoardView(
    val sideToMove: Color,
    val cells: List<CellView>,
    val universeCount: Int,
    /** Probability that the side to move is currently in check (0 or 1 in classic chess). */
    val checkProbability: Double,
    val lastMove: GameMove?,
) {
    fun cell(square: Square): CellView = cells[square.index]
}
