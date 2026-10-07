package ru.werelaxe.chess.core

import kotlinx.serialization.Serializable

/**
 * An immutable classical chess position: piece placement, side to move, castling rights
 * and the en passant target square. Two boards are equal when all four match, which is
 * exactly the equivalence used to merge quantum universes.
 *
 * [enPassant] is set only when an en passant capture is actually legal, so that positions
 * differing only by an unusable en passant square compare equal (as FIDE's repetition rule requires).
 */
@Serializable
data class Board(
    val squares: List<Piece?>,
    val sideToMove: Color = Color.WHITE,
    val castling: CastlingRights = CastlingRights.ALL,
    val enPassant: Square? = null,
) {
    init {
        require(squares.size == 64) { "A board must have 64 squares, got ${squares.size}" }
    }

    operator fun get(square: Square): Piece? = squares[square.index]

    fun isEmpty(square: Square): Boolean = squares[square.index] == null

    fun kingSquare(color: Color): Square? {
        val king = Piece(color, PieceType.KING)
        val index = squares.indexOf(king)
        return if (index >= 0) Square(index) else null
    }

    fun hasKing(color: Color): Boolean = kingSquare(color) != null

    /** All (square, piece) pairs for the given color. */
    fun pieces(color: Color): List<Pair<Square, Piece>> = buildList {
        squares.forEachIndexed { index, piece ->
            if (piece != null && piece.color == color) add(Square(index) to piece)
        }
    }

    fun pieceCount(): Int = squares.count { it != null }

    private val cachedHash: Int by lazy { squares.hashCode() * 31 + sideToMove.ordinal * 7 + castling.hashCode() * 3 + (enPassant?.index ?: -1) }

    override fun hashCode(): Int = cachedHash

    override fun toString(): String = Fen.format(this)

    companion object {
        fun empty(sideToMove: Color = Color.WHITE): Board =
            Board(List(64) { null }, sideToMove, CastlingRights.NONE, null)

        /** The standard starting position. */
        fun initial(): Board = Fen.parse(Fen.INITIAL).board
    }
}
