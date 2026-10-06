package ru.werelaxe.chess.core

import kotlinx.serialization.Serializable

@Serializable
enum class PieceType(val symbol: Char) {
    PAWN('p'),
    KNIGHT('n'),
    BISHOP('b'),
    ROOK('r'),
    QUEEN('q'),
    KING('k');

    companion object {
        /** Piece types a pawn may promote to. */
        val PROMOTIONS: List<PieceType> = listOf(QUEEN, ROOK, BISHOP, KNIGHT)

        fun fromSymbol(symbol: Char): PieceType? = entries.firstOrNull { it.symbol == symbol.lowercaseChar() }
    }
}

@Serializable
data class Piece(val color: Color, val type: PieceType) {
    /** FEN character: uppercase for white, lowercase for black. */
    val fenChar: Char
        get() = if (color == Color.WHITE) type.symbol.uppercaseChar() else type.symbol

    override fun toString(): String = fenChar.toString()

    companion object {
        fun fromFenChar(char: Char): Piece? {
            val type = PieceType.fromSymbol(char) ?: return null
            return Piece(if (char.isUpperCase()) Color.WHITE else Color.BLACK, type)
        }

        val WHITE_KING = Piece(Color.WHITE, PieceType.KING)
        val BLACK_KING = Piece(Color.BLACK, PieceType.KING)
    }
}
