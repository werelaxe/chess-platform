package ru.werelaxe.chess.core

data class FenPosition(
    val board: Board,
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
)

/** Forsyth-Edwards Notation parsing and formatting. */
object Fen {
    const val INITIAL = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    fun parse(fen: String): FenPosition {
        val fields = fen.trim().split(Regex("\\s+"))
        require(fields.size >= 2) { "Invalid FEN: '$fen'" }

        val squares = MutableList<Piece?>(64) { null }
        val ranks = fields[0].split('/')
        require(ranks.size == 8) { "Invalid FEN placement: '${fields[0]}'" }
        ranks.forEachIndexed { rowIndex, row ->
            val rank = 7 - rowIndex
            var file = 0
            for (char in row) {
                if (char.isDigit()) {
                    file += char - '0'
                } else {
                    val piece = requireNotNull(Piece.fromFenChar(char)) { "Invalid FEN piece '$char'" }
                    require(file < 8) { "Invalid FEN rank: '$row'" }
                    squares[rank * 8 + file] = piece
                    file++
                }
            }
            require(file == 8) { "Invalid FEN rank: '$row'" }
        }

        // A side may lack a king (quantum universes after a capture) but never have two.
        for (color in Color.entries) {
            val kings = squares.count { it == Piece(color, PieceType.KING) }
            require(kings <= 1) { "Invalid FEN: found $kings ${color.name.lowercase()} kings" }
        }

        val sideToMove = when (fields[1]) {
            "w" -> Color.WHITE
            "b" -> Color.BLACK
            else -> throw IllegalArgumentException("Invalid FEN side to move: '${fields[1]}'")
        }
        val castling = if (fields.size > 2) CastlingRights.parseFen(fields[2]) else CastlingRights.NONE
        val rawEnPassant = if (fields.size > 3 && fields[3] != "-") Square.parse(fields[3]) else null
        val halfmoveClock = fields.getOrNull(4)?.toIntOrNull() ?: 0
        val fullmoveNumber = fields.getOrNull(5)?.toIntOrNull() ?: 1

        val board = Board(squares, sideToMove, sanitizeCastling(squares, castling), null)
        val enPassant = rawEnPassant?.takeIf { ClassicRules.hasLegalEnPassantCapture(board, it) }
        return FenPosition(board.copy(enPassant = enPassant), halfmoveClock, fullmoveNumber)
    }

    /** Drops castling rights whose king or rook is not on its initial square. */
    private fun sanitizeCastling(squares: List<Piece?>, rights: CastlingRights): CastlingRights {
        var result = rights
        for (color in Color.entries) {
            val king = squares[CastlingRights.kingSquare(color).index]
            if (king != Piece(color, PieceType.KING)) {
                result = result.without(color)
                continue
            }
            for (side in CastlingSide.entries) {
                val rook = squares[CastlingRights.rookSquare(color, side).index]
                if (rook != Piece(color, PieceType.ROOK)) {
                    result = result.without(color, side)
                }
            }
        }
        return result
    }

    fun format(board: Board, halfmoveClock: Int = 0, fullmoveNumber: Int = 1): String {
        val placement = (7 downTo 0).joinToString("/") { rank ->
            buildString {
                var empty = 0
                for (file in 0..7) {
                    val piece = board.squares[rank * 8 + file]
                    if (piece == null) {
                        empty++
                    } else {
                        if (empty > 0) {
                            append(empty)
                            empty = 0
                        }
                        append(piece.fenChar)
                    }
                }
                if (empty > 0) append(empty)
            }
        }
        val side = if (board.sideToMove == Color.WHITE) "w" else "b"
        val enPassant = board.enPassant?.name ?: "-"
        return "$placement $side ${board.castling.fen} $enPassant $halfmoveClock $fullmoveNumber"
    }
}
