package dialects.chess.classic

import core.Coordinate
import core.State
import dialects.checkers.CheckersFigure
import dialects.checkers.CheckersFigureType
import dialects.checkers.CheckersPlayer

class ChessState: State<ChessFigure>(ChessPlayer.WHITE) {
    val width = 8
    val height = 8

    var blackKingPosition = Coordinate.of(width - 4, 0)
    var whiteKingPosition = Coordinate.of(width - 4, height - 1)

    fun isBlackStep() = currentPlayer == ChessPlayer.BLACK
    fun isWhiteStep() = currentPlayer == ChessPlayer.WHITE

    val kingPosition: Coordinate
        get() = if (isWhiteStep()) whiteKingPosition else blackKingPosition

    private val board: MutableList<MutableList<ChessFigure?>> = MutableList(height) { MutableList(8) { null } }

    init {
        for (i in 0 until width) {
            board[1][i] = ChessFigure(ChessPlayer.BLACK, ChessFigureType.PAWN)
            board[6][i] = ChessFigure(ChessPlayer.WHITE, ChessFigureType.PAWN)
        }
        board[0][0] = ChessFigure(ChessPlayer.BLACK, ChessFigureType.ROOK)
        board[0][width - 1] = ChessFigure(ChessPlayer.BLACK, ChessFigureType.ROOK)
        board[0][1] = ChessFigure(ChessPlayer.BLACK, ChessFigureType.KNIGHT)
        board[0][width - 2] = ChessFigure(ChessPlayer.BLACK, ChessFigureType.KNIGHT)
        board[0][2] = ChessFigure(ChessPlayer.BLACK, ChessFigureType.BISHOP)
        board[0][width - 3] = ChessFigure(ChessPlayer.BLACK, ChessFigureType.BISHOP)
        board[0][3] = ChessFigure(ChessPlayer.BLACK, ChessFigureType.QUEEN)
        board[0][width - 4] = ChessFigure(ChessPlayer.BLACK, ChessFigureType.KING)

        board[height - 1][0] = ChessFigure(ChessPlayer.WHITE, ChessFigureType.ROOK)
        board[height - 1][width - 1] = ChessFigure(ChessPlayer.WHITE, ChessFigureType.ROOK)
        board[height - 1][1] = ChessFigure(ChessPlayer.WHITE, ChessFigureType.KNIGHT)
        board[height - 1][width - 2] = ChessFigure(ChessPlayer.WHITE, ChessFigureType.KNIGHT)
        board[height - 1][2] = ChessFigure(ChessPlayer.WHITE, ChessFigureType.BISHOP)
        board[height - 1][width - 3] = ChessFigure(ChessPlayer.WHITE, ChessFigureType.BISHOP)
        board[height - 1][3] = ChessFigure(ChessPlayer.WHITE, ChessFigureType.QUEEN)
        board[height - 1][width - 4] = ChessFigure(ChessPlayer.WHITE, ChessFigureType.KING)
    }

    override fun getEl(coord: Coordinate): ChessFigure? {
        return board[coord.y()][coord.x()]
    }

    override fun setEl(coord: Coordinate, figure: ChessFigure?) {
        figure?.let {
            if (figure.figureType == ChessFigureType.KING) {
                if (figure.owner == ChessPlayer.BLACK) {
                    blackKingPosition = coord
                } else {
                    whiteKingPosition = coord
                }
            }
        }
        board[coord.y()][coord.x()] = figure
    }

    override fun contains(coord: Coordinate) = coord.x() in 0 until width && coord.y() in 0 until height
}
