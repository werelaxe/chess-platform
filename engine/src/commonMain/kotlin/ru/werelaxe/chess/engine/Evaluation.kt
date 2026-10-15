package ru.werelaxe.chess.engine

import ru.werelaxe.chess.core.Board
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.Piece
import ru.werelaxe.chess.core.PieceType
import ru.werelaxe.chess.core.Square
import kotlin.math.abs

/**
 * Static evaluation in centipawns: material plus piece-square tables (the classic
 * "simplified evaluation function"), and a mop-up term for the side that is winning a bare
 * endgame. Positive values favour the given perspective.
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

    /** The value of a draw, by repetition or otherwise, for either side. */
    const val DRAW = 0

    /** Per square of distance between the defending king and the centre, in the mop-up. */
    const val MOP_UP_EDGE = 10

    /** Per square of closeness between the two kings, in the mop-up. */
    const val MOP_UP_CLOSE = 4

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

    /** What one side has on the board besides its king. */
    private class Material {
        var pawns = 0
        var pieces = 0
        var king = -1

        val total: Int
            get() = pawns + pieces
    }

    /**
     * Material and piece placement from [perspective]'s point of view. Kings count as zero.
     * The endgame tables apply once both sides are down to a queen or less in non-pawn material.
     */
    fun evaluate(board: Board, perspective: Color): Int {
        val (white, black) = material(board)
        val endgame = white.pieces <= QUEEN && black.pieces <= QUEEN
        val squares = board.squares
        var score = 0
        for (index in squares.indices) {
            val piece = squares[index] ?: continue
            val sign = if (piece.color == perspective) 1 else -1
            score += sign * (value(piece.type) + positional(piece, Square(index), endgame))
        }
        score += if (perspective == Color.WHITE) mopUp(white, black) - mopUp(black, white) else mopUp(black, white) - mopUp(white, black)
        return score
    }

    /** What is on [board] besides the kings, for each side. */
    private fun material(board: Board): Pair<Material, Material> {
        val white = Material()
        val black = Material()
        val squares = board.squares
        for (index in squares.indices) {
            val piece = squares[index] ?: continue
            val side = if (piece.color == Color.WHITE) white else black
            when (piece.type) {
                PieceType.PAWN -> side.pawns += PAWN
                PieceType.KING -> side.king = index
                else -> side.pieces += value(piece.type)
            }
        }
        return white to black
    }

    /**
     * Whether one side is ahead by at least a rook against little more than a bare king (a
     * minor piece and a pawn at most): the endgames that are won by technique, where the
     * mop-up applies and a deeper search pays off.
     */
    fun isBareEndgame(board: Board): Boolean {
        val (white, black) = material(board)
        return isMopUp(white, black) || isMopUp(black, white)
    }

    private fun isMopUp(strong: Material, weak: Material): Boolean =
        strong.total - weak.total >= ROOK && weak.total <= BISHOP + PAWN

    /**
     * The mop-up bonus of [strong] against [weak]: a side ahead by at least a rook against
     * little more than a bare king (a minor piece and a pawn at most) is rewarded for driving
     * the enemy king towards the edge and bringing its own king closer, which is what wins such
     * endgames and what the tables alone never ask for. Zero unless both kings are on the board.
     */
    private fun mopUp(strong: Material, weak: Material): Int {
        if (!isMopUp(strong, weak)) return 0
        if (strong.king < 0 || weak.king < 0) return 0
        val weakKing = Square(weak.king)
        val strongKing = Square(strong.king)
        val fromCentre = centreDistance(weakKing.file) + centreDistance(weakKing.rank)
        val between = abs(weakKing.file - strongKing.file) + abs(weakKing.rank - strongKing.rank)
        return MOP_UP_EDGE * fromCentre + MOP_UP_CLOSE * (MAX_KING_DISTANCE - between)
    }

    /** Distance of a file or rank from the two central ones: 0 for d/e (or ranks 4/5), 3 at the edge. */
    private fun centreDistance(line: Int): Int = if (line <= 3) 3 - line else line - 4

    /** The largest Manhattan distance between two squares. */
    private const val MAX_KING_DISTANCE = 14
}
