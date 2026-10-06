package ru.werelaxe.chess.core

import kotlinx.serialization.Serializable

/**
 * A classical chess move. Castling is encoded as the king moving two files;
 * en passant is implied by the position. [promotion] is set only for pawn promotions.
 */
@Serializable
data class Move(
    val from: Square,
    val to: Square,
    val promotion: PieceType? = null,
) {
    /** UCI notation, e.g. "e2e4" or "e7e8q". */
    val uci: String
        get() = from.name + to.name + (promotion?.symbol?.toString() ?: "")

    override fun toString(): String = uci

    companion object {
        fun parseUci(uci: String): Move {
            require(uci.length == 4 || uci.length == 5) { "Invalid UCI move: '$uci'" }
            val promotion = if (uci.length == 5) {
                requireNotNull(PieceType.fromSymbol(uci[4])) { "Invalid promotion piece in '$uci'" }
            } else {
                null
            }
            return Move(Square.parse(uci.substring(0, 2)), Square.parse(uci.substring(2, 4)), promotion)
        }
    }
}

class IllegalMoveException(message: String) : RuntimeException(message)
