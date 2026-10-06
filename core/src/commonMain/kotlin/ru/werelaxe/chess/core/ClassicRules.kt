package ru.werelaxe.chess.core

import kotlin.math.abs

/**
 * Classical chess move generation and move application on an immutable [Board].
 *
 * Legal move generation never relies on the opponent having played legally: a position where
 * the opponent's king is capturable (which happens inside quantum universes) is handled by
 * simply allowing the capture, and a side without a king is never considered in check.
 */
object ClassicRules {
    private val KNIGHT_OFFSETS = listOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)
    private val KING_OFFSETS = listOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)
    private val ROOK_DIRECTIONS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private val BISHOP_DIRECTIONS = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
    private val QUEEN_DIRECTIONS = ROOK_DIRECTIONS + BISHOP_DIRECTIONS

    /** Whether [square] is attacked by any piece of color [by]. */
    fun isAttacked(board: Board, square: Square, by: Color): Boolean {
        for (fileDelta in intArrayOf(-1, 1)) {
            val from = square.offset(fileDelta, -by.pawnDirection) ?: continue
            if (board[from] == Piece(by, PieceType.PAWN)) return true
        }
        for ((df, dr) in KNIGHT_OFFSETS) {
            val from = square.offset(df, dr) ?: continue
            if (board[from] == Piece(by, PieceType.KNIGHT)) return true
        }
        for ((df, dr) in KING_OFFSETS) {
            val from = square.offset(df, dr) ?: continue
            if (board[from] == Piece(by, PieceType.KING)) return true
        }
        if (slidingAttack(board, square, by, ROOK_DIRECTIONS, PieceType.ROOK)) return true
        if (slidingAttack(board, square, by, BISHOP_DIRECTIONS, PieceType.BISHOP)) return true
        return false
    }

    private fun slidingAttack(board: Board, square: Square, by: Color, directions: List<Pair<Int, Int>>, slider: PieceType): Boolean {
        for ((df, dr) in directions) {
            var current = square.offset(df, dr)
            while (current != null) {
                val piece = board[current]
                if (piece != null) {
                    if (piece.color == by && (piece.type == slider || piece.type == PieceType.QUEEN)) return true
                    break
                }
                current = current.offset(df, dr)
            }
        }
        return false
    }

    fun isInCheck(board: Board, color: Color): Boolean {
        val king = board.kingSquare(color) ?: return false
        return isAttacked(board, king, color.opposite)
    }

    /** Moves of the piece on [from] that obey piece movement rules but may leave the own king in check. */
    fun pseudoLegalMoves(board: Board, from: Square): List<Move> {
        val piece = board[from] ?: return emptyList()
        if (piece.color != board.sideToMove) return emptyList()
        val moves = ArrayList<Move>(28)
        when (piece.type) {
            PieceType.PAWN -> pawnMoves(board, from, piece.color, moves)
            PieceType.KNIGHT -> jumpMoves(board, from, piece.color, KNIGHT_OFFSETS, moves)
            PieceType.BISHOP -> slidingMoves(board, from, piece.color, BISHOP_DIRECTIONS, moves)
            PieceType.ROOK -> slidingMoves(board, from, piece.color, ROOK_DIRECTIONS, moves)
            PieceType.QUEEN -> slidingMoves(board, from, piece.color, QUEEN_DIRECTIONS, moves)
            PieceType.KING -> {
                jumpMoves(board, from, piece.color, KING_OFFSETS, moves)
                castlingMoves(board, from, piece.color, moves)
            }
        }
        return moves
    }

    private fun pawnMoves(board: Board, from: Square, color: Color, out: MutableList<Move>) {
        val direction = color.pawnDirection
        val promotes = from.rank + direction == color.promotionRank

        fun add(to: Square) {
            if (promotes) {
                for (promotion in PieceType.PROMOTIONS) out.add(Move(from, to, promotion))
            } else {
                out.add(Move(from, to))
            }
        }

        val oneStep = from.offset(0, direction)
        if (oneStep != null && board.isEmpty(oneStep)) {
            add(oneStep)
            if (from.rank == color.pawnStartRank) {
                val twoSteps = from.offset(0, 2 * direction)
                if (twoSteps != null && board.isEmpty(twoSteps)) out.add(Move(from, twoSteps))
            }
        }
        for (fileDelta in intArrayOf(-1, 1)) {
            val to = from.offset(fileDelta, direction) ?: continue
            val target = board[to]
            if (target != null) {
                if (target.color != color) add(to)
            } else if (to == board.enPassant) {
                out.add(Move(from, to))
            }
        }
    }

    private fun jumpMoves(board: Board, from: Square, color: Color, offsets: List<Pair<Int, Int>>, out: MutableList<Move>) {
        for ((df, dr) in offsets) {
            val to = from.offset(df, dr) ?: continue
            if (board[to]?.color != color) out.add(Move(from, to))
        }
    }

    private fun slidingMoves(board: Board, from: Square, color: Color, directions: List<Pair<Int, Int>>, out: MutableList<Move>) {
        for ((df, dr) in directions) {
            var to = from.offset(df, dr)
            while (to != null) {
                val target = board[to]
                if (target == null) {
                    out.add(Move(from, to))
                } else {
                    if (target.color != color) out.add(Move(from, to))
                    break
                }
                to = to.offset(df, dr)
            }
        }
    }

    private fun castlingMoves(board: Board, from: Square, color: Color, out: MutableList<Move>) {
        if (from != CastlingRights.kingSquare(color)) return
        if (isAttacked(board, from, color.opposite)) return
        val rank = color.homeRank
        for (side in CastlingSide.entries) {
            if (!board.castling.has(color, side)) continue
            if (board[CastlingRights.rookSquare(color, side)] != Piece(color, PieceType.ROOK)) continue
            val mustBeEmpty = if (side == CastlingSide.KING_SIDE) intArrayOf(5, 6) else intArrayOf(1, 2, 3)
            if (mustBeEmpty.any { !board.isEmpty(Square.of(it, rank)) }) continue
            val kingPath = if (side == CastlingSide.KING_SIDE) intArrayOf(5, 6) else intArrayOf(3, 2)
            if (kingPath.any { isAttacked(board, Square.of(it, rank), color.opposite) }) continue
            out.add(Move(from, Square.of(if (side == CastlingSide.KING_SIDE) 6 else 2, rank)))
        }
    }

    fun legalMoves(board: Board, from: Square): List<Move> =
        pseudoLegalMoves(board, from).filter { !leavesKingInCheck(board, it) }

    fun legalMoves(board: Board): List<Move> =
        board.pieces(board.sideToMove).flatMap { (square, _) -> legalMoves(board, square) }

    fun hasLegalMoves(board: Board): Boolean =
        board.pieces(board.sideToMove).any { (square, _) -> legalMoves(board, square).isNotEmpty() }

    fun isLegal(board: Board, move: Move): Boolean = move in legalMoves(board, move.from)

    private fun leavesKingInCheck(board: Board, move: Move): Boolean =
        isInCheck(apply(board, move), board.sideToMove)

    /** Whether the side to move could capture en passant on [enPassant] (ignoring pins). */
    fun canCaptureEnPassant(board: Board, enPassant: Square): Boolean {
        val color = board.sideToMove
        val pawn = Piece(color, PieceType.PAWN)
        for (fileDelta in intArrayOf(-1, 1)) {
            val from = enPassant.offset(fileDelta, -color.pawnDirection) ?: continue
            if (board[from] == pawn) return true
        }
        return false
    }

    fun isCapture(board: Board, move: Move): Boolean {
        if (board[move.to] != null) return true
        val piece = board[move.from] ?: return false
        return piece.type == PieceType.PAWN && move.from.file != move.to.file
    }

    fun isPawnMove(board: Board, move: Move): Boolean = board[move.from]?.type == PieceType.PAWN

    /** A move that resets the fifty-move counter: a capture or a pawn move. */
    fun isIrreversible(board: Board, move: Move): Boolean = isCapture(board, move) || isPawnMove(board, move)

    /**
     * Applies a pseudo-legal move and returns the resulting position. Handles castling,
     * en passant, promotion, castling-right updates and the en passant target square.
     */
    fun apply(board: Board, move: Move): Board {
        val piece = requireNotNull(board[move.from]) { "No piece on ${move.from}" }
        val color = piece.color
        val squares = board.squares.toMutableList()
        val captured = board[move.to]
        squares[move.to.index] = piece
        squares[move.from.index] = null

        var castling = board.castling
        var enPassant: Square? = null

        when (piece.type) {
            PieceType.PAWN -> {
                if (captured == null && move.from.file != move.to.file) {
                    // En passant: the captured pawn stands beside the origin square.
                    squares[Square.of(move.to.file, move.from.rank).index] = null
                }
                if (move.promotion != null) {
                    squares[move.to.index] = Piece(color, move.promotion)
                }
                if (abs(move.to.rank - move.from.rank) == 2) {
                    val target = Square.of(move.from.file, (move.from.rank + move.to.rank) / 2)
                    val enemyPawn = Piece(color.opposite, PieceType.PAWN)
                    val capturable = intArrayOf(-1, 1).any { df ->
                        move.to.offset(df, 0)?.let { squares[it.index] == enemyPawn } == true
                    }
                    if (capturable) enPassant = target
                }
            }
            PieceType.KING -> {
                castling = castling.without(color)
                if (abs(move.to.file - move.from.file) == 2) {
                    val side = if (move.to.file == 6) CastlingSide.KING_SIDE else CastlingSide.QUEEN_SIDE
                    val rookFrom = CastlingRights.rookSquare(color, side)
                    val rookTo = Square.of(if (side == CastlingSide.KING_SIDE) 5 else 3, color.homeRank)
                    squares[rookTo.index] = squares[rookFrom.index]
                    squares[rookFrom.index] = null
                }
            }
            PieceType.ROOK -> {
                for (side in CastlingSide.entries) {
                    if (move.from == CastlingRights.rookSquare(color, side)) castling = castling.without(color, side)
                }
            }
            else -> {}
        }

        if (captured != null) {
            if (captured.type == PieceType.ROOK) {
                for (side in CastlingSide.entries) {
                    if (move.to == CastlingRights.rookSquare(captured.color, side)) {
                        castling = castling.without(captured.color, side)
                    }
                }
            }
            if (captured.type == PieceType.KING) {
                castling = castling.without(captured.color)
            }
        }

        return Board(squares, color.opposite, castling, enPassant)
    }

    /** Passes the turn without moving: used for quantum universes where a move does not apply. */
    fun pass(board: Board): Board = Board(board.squares, board.sideToMove.opposite, board.castling, null)

    /**
     * Whether neither side can possibly deliver checkmate: bare kings, king and a single
     * minor piece, or only bishops that all stand on squares of the same color.
     */
    fun isInsufficientMaterial(board: Board): Boolean {
        val pieces = board.squares.withIndex().filter { it.value != null && it.value!!.type != PieceType.KING }
        if (pieces.isEmpty()) return true
        if (pieces.size == 1) {
            val type = pieces[0].value!!.type
            return type == PieceType.BISHOP || type == PieceType.KNIGHT
        }
        if (pieces.all { it.value!!.type == PieceType.BISHOP }) {
            val colors = pieces.map { (Square(it.index).file + Square(it.index).rank) % 2 }.toSet()
            return colors.size == 1
        }
        return false
    }

    /** Number of leaf nodes of the legal move tree at the given depth (used to validate move generation). */
    fun perft(board: Board, depth: Int): Long {
        if (depth == 0) return 1
        val moves = legalMoves(board)
        if (depth == 1) return moves.size.toLong()
        var total = 0L
        for (move in moves) total += perft(apply(board, move), depth - 1)
        return total
    }
}
