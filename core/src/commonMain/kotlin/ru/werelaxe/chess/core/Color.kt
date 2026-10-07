package ru.werelaxe.chess.core

import kotlinx.serialization.Serializable

@Serializable
enum class Color {
    WHITE,
    BLACK;

    val opposite: Color
        get() = if (this == WHITE) BLACK else WHITE

    /** Direction in which this color's pawns advance, in ranks. */
    val pawnDirection: Int
        get() = if (this == WHITE) 1 else -1

    /** Rank index (0-based) where this color's pawns start. */
    val pawnStartRank: Int
        get() = if (this == WHITE) 1 else 6

    /** Rank index (0-based) on which this color's pawns promote. */
    val promotionRank: Int
        get() = if (this == WHITE) 7 else 0

    /** Rank index (0-based) of this color's king and rooks at the start. */
    val homeRank: Int
        get() = if (this == WHITE) 0 else 7
}
