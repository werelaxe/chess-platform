package ru.werelaxe.chess.core.engine

import ru.werelaxe.chess.core.Board
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.Piece
import ru.werelaxe.chess.core.PieceType
import ru.werelaxe.chess.core.Square

/**
 * Static evaluation in centipawns: material plus piece-square tables (the classic
 * "simplified evaluation function"). Positive values favour the given perspective.
 */
object Evaluation {
    const val PAWN = 100
    const val KNIGHT = 320
    const val BISHOP = 330
    const val ROOK = 500
    const val QUEEN = 900

    /** Score of a mate; real evaluations stay far below this. */
    const val MATE = 100_000

    /** Value attached to a king that exists in a quantum universe where the opponent's does not. */
    const val KING_PRESENCE = 6_000

    fun value(type: PieceType): Int = when (type) {
        PieceType.PAWN -> PAWN
        PieceType.KNIGHT -> KNIGHT
        PieceType.BISHOP -> BISHOP
        PieceType.ROOK -> ROOK
        PieceType.QUEEN -> QUEEN
        PieceType.KING -> 0
    }

    // Tables are written from white's point of view with the eighth rank on the first row.
    private val PAWN_TABLE = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
        5, 5, 10, 25, 25, 10, 5, 5,
        0, 0, 0, 20, 20, 0, 0, 0,
        5, -5, -10, 0, 0, -10, -5, 5,
        5, 10, 10, -20, -20, 10, 10, 5,
        0, 0, 0, 0, 0, 0, 0, 0,
    )
    private val KNIGHT_TABLE = intArrayOf(
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20, 0, 0, 0, 0, -20, -40,
        -30, 0, 10, 15, 15, 10, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30,
        -30, 0, 15, 20, 20, 15, 0, -30,
        -30, 5, 10, 15, 15, 10, 5, -30,
        -40, -20, 0, 5, 5, 0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50,
    )
    private val BISHOP_TABLE = intArrayOf(
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 10, 10, 5, 0, -10,
        -10, 5, 5, 10, 10, 5, 5, -10,
        -10, 0, 10, 10, 10, 10, 0, -10,
        -10, 10, 10, 10, 10, 10, 10, -10,
        -10, 5, 0, 0, 0, 0, 5, -10,
        -20, -10, -10, -10, -10, -10, -10, -20,
    )
    private val ROOK_TABLE = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        5, 10, 10, 10, 10, 10, 10, 5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        0, 0, 0, 5, 5, 0, 0, 0,
    )
    private val QUEEN_TABLE = intArrayOf(
        -20, -10, -10, -5, -5, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 5, 5, 5, 0, -10,
        -5, 0, 5, 5, 5, 5, 0, -5,
        0, 0, 5, 5, 5, 5, 0, -5,
        -10, 5, 5, 5, 5, 5, 0, -10,
        -10, 0, 5, 0, 0, 0, 0, -10,
        -20, -10, -10, -5, -5, -10, -10, -20,
    )
    private val KING_MIDDLEGAME_TABLE = intArrayOf(
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -10, -20, -20, -20, -20, -20, -20, -10,
        20, 20, 0, 0, 0, 0, 20, 20,
        20, 30, 10, 0, 0, 10, 30, 20,
    )
    private val KING_ENDGAME_TABLE = intArrayOf(
        -50, -40, -30, -20, -20, -30, -40, -50,
        -30, -20, -10, 0, 0, -10, -20, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -30, 0, 0, 0, 0, -30, -30,
        -50, -30, -30, -30, -30, -30, -30, -50,
    )

    private fun tableIndex(square: Square, color: Color): Int =
        if (color == Color.WHITE) (7 - square.rank) * 8 + square.file else square.rank * 8 + square.file

    private fun positional(piece: Piece, square: Square, endgame: Boolean): Int {
        val index = tableIndex(square, piece.color)
        return when (piece.type) {
            PieceType.PAWN -> PAWN_TABLE[index]
            PieceType.KNIGHT -> KNIGHT_TABLE[index]
            PieceType.BISHOP -> BISHOP_TABLE[index]
            PieceType.ROOK -> ROOK_TABLE[index]
            PieceType.QUEEN -> QUEEN_TABLE[index]
            PieceType.KING -> if (endgame) KING_ENDGAME_TABLE[index] else KING_MIDDLEGAME_TABLE[index]
        }
    }

    /** Endgame once both sides are down to a queen or less in non-pawn material. */
    private fun isEndgame(board: Board): Boolean {
        var white = 0
        var black = 0
        for (piece in board.squares) {
            if (piece == null || piece.type == PieceType.PAWN || piece.type == PieceType.KING) continue
            if (piece.color == Color.WHITE) white += value(piece.type) else black += value(piece.type)
        }
        return white <= QUEEN && black <= QUEEN
    }

    /** Material and piece placement from [perspective]'s point of view. Kings count as zero. */
    fun evaluate(board: Board, perspective: Color): Int {
        val endgame = isEndgame(board)
        var score = 0
        val squares = board.squares
        for (index in squares.indices) {
            val piece = squares[index] ?: continue
            val sign = if (piece.color == perspective) 1 else -1
            score += sign * (value(piece.type) + positional(piece, Square(index), endgame))
        }
        return score
    }
}
