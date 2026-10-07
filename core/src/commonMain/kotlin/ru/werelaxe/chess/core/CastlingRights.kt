package ru.werelaxe.chess.core

import kotlinx.serialization.Serializable

enum class CastlingSide {
    KING_SIDE,
    QUEEN_SIDE,
}

@Serializable
data class CastlingRights(
    val whiteKingSide: Boolean = true,
    val whiteQueenSide: Boolean = true,
    val blackKingSide: Boolean = true,
    val blackQueenSide: Boolean = true,
) {
    fun has(color: Color, side: CastlingSide): Boolean = when (color) {
        Color.WHITE -> if (side == CastlingSide.KING_SIDE) whiteKingSide else whiteQueenSide
        Color.BLACK -> if (side == CastlingSide.KING_SIDE) blackKingSide else blackQueenSide
    }

    fun without(color: Color): CastlingRights = when (color) {
        Color.WHITE -> copy(whiteKingSide = false, whiteQueenSide = false)
        Color.BLACK -> copy(blackKingSide = false, blackQueenSide = false)
    }

    fun without(color: Color, side: CastlingSide): CastlingRights = when (color) {
        Color.WHITE -> if (side == CastlingSide.KING_SIDE) copy(whiteKingSide = false) else copy(whiteQueenSide = false)
        Color.BLACK -> if (side == CastlingSide.KING_SIDE) copy(blackKingSide = false) else copy(blackQueenSide = false)
    }

    val isEmpty: Boolean
        get() = !whiteKingSide && !whiteQueenSide && !blackKingSide && !blackQueenSide

    /** FEN castling field, e.g. "KQkq" or "-". */
    val fen: String
        get() = buildString {
            if (whiteKingSide) append('K')
            if (whiteQueenSide) append('Q')
            if (blackKingSide) append('k')
            if (blackQueenSide) append('q')
            if (isEmpty) append('-')
        }

    companion object {
        val ALL = CastlingRights()
        val NONE = CastlingRights(false, false, false, false)

        fun parseFen(field: String): CastlingRights = CastlingRights(
            whiteKingSide = 'K' in field,
            whiteQueenSide = 'Q' in field,
            blackKingSide = 'k' in field,
            blackQueenSide = 'q' in field,
        )

        /** Initial square of the rook taking part in castling for the given color and side. */
        fun rookSquare(color: Color, side: CastlingSide): Square =
            Square.of(if (side == CastlingSide.KING_SIDE) 7 else 0, color.homeRank)

        fun kingSquare(color: Color): Square = Square.of(4, color.homeRank)
    }
}
