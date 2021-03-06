package dialects.chess.classic

import core.Coordinate
import core.State
import kotlin.math.abs

open class ChessState(
    val width: Int,
    val height: Int,
    val board: MutableList<MutableList<ChessFigure?>>,
    val context: Context = Context(),
    startPlayer: Int = ChessPlayer.WHITE
): State<ChessFigure>(startPlayer) {
    constructor(width: Int = 8, height: Int = 8): this(width, height, genStartBoard(width, height))

    var blackKingPosition = Coordinate.of(width - 4, 0)
    var whiteKingPosition = Coordinate.of(width - 4, height - 1)

    fun isBlackStep() = currentPlayer == ChessPlayer.BLACK
    fun isWhiteStep() = currentPlayer == ChessPlayer.WHITE

    val kingPosition: Coordinate
        get() = if (isWhiteStep()) whiteKingPosition else blackKingPosition


    override fun getEl(coord: Coordinate): ChessFigure? {
        return board[coord.y()][coord.x()]
    }

    override fun setEl(coord: Coordinate, figure: ChessFigure?) {
        if (coord.x() == 3 && coord.y() == 3) {
            val a = 1
        }
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

    var enPassantPair: Pair<Coordinate, Coordinate>? = null
        protected set

    fun testMove(from: Coordinate, to: Coordinate) {
        super.move(from, to)
    }

    private fun isDoubleSquarePawnMove(from: Coordinate, to: Coordinate): Boolean {
        this[to]?.let { fig ->
            if (fig.figureType == ChessFigureType.PAWN && abs(to.y() - from.y()) == 2) {
                return true
            }
        }
        return false
    }

    fun isEnPassantMove(from: Coordinate, to: Coordinate): Boolean {
        this[from]?.let { fromFig ->
            return fromFig.figureType == ChessFigureType.PAWN &&
                this[to] == null &&
                abs(from.x() - to.x()) == 1 &&
                abs(from.y() - to.y()) == 1 &&
                ((fromFig.owner == ChessPlayer.BLACK && from.y() == 4) ||
                    (fromFig.owner == ChessPlayer.WHITE && from.y() == 3))
        }
        return false
    }

    override fun move(from: Coordinate, to: Coordinate) {
        super.move(from, to)
        enPassantPair =
            if (isDoubleSquarePawnMove(from, to)) (to + from) / 2 to to
            else null
        this[to]?.let { toFig ->
            if (toFig.figureType == ChessFigureType.KING || toFig.figureType == ChessFigureType.ROOK) {
                this[to] = this[to]?.copy(canCastling = false)
            }
        }
    }

    override fun contains(coord: Coordinate) = coord.x() in 0 until width && coord.y() in 0 until height

    class Context {
        var enPassantPair: Pair<Coordinate, Coordinate>? = null
        var castlingPostMove: Pair<Coordinate, Coordinate>? = null
    }

    protected fun MutableList<MutableList<ChessFigure?>>.clone() = MutableList(this.size) { y ->
        MutableList(this[y].size) { x ->
            this[y][x]?.copy()
        }
    }

    open fun clone(): ChessState {
        val boardClone = board.clone()
        return with(ChessState(width, height, boardClone, startPlayer = currentPlayer)) {
            this.enPassantPair = enPassantPair
            this.blackKingPosition = this@ChessState.blackKingPosition
            this.whiteKingPosition = this@ChessState.whiteKingPosition
            this
        }
    }
}


fun genStartBoard(width: Int, height: Int): MutableList<MutableList<ChessFigure?>> {
    val boardResult: MutableList<MutableList<ChessFigure?>> = MutableList(height) { MutableList(width) { null } }

    for (i in 0 until width) {
        boardResult[1][i] = ChessFigure(ChessPlayer.BLACK, ChessFigureType.PAWN)
        boardResult[6][i] = ChessFigure(ChessPlayer.WHITE, ChessFigureType.PAWN)
    }
    boardResult[0][0] = ChessFigure(ChessPlayer.BLACK, ChessFigureType.ROOK, true)
    boardResult[0][width - 1] = ChessFigure(ChessPlayer.BLACK, ChessFigureType.ROOK, true)
    boardResult[0][1] = ChessFigure(ChessPlayer.BLACK, ChessFigureType.KNIGHT)
    boardResult[0][width - 2] = ChessFigure(ChessPlayer.BLACK, ChessFigureType.KNIGHT)
    boardResult[0][2] = ChessFigure(ChessPlayer.BLACK, ChessFigureType.BISHOP)
    boardResult[0][width - 3] = ChessFigure(ChessPlayer.BLACK, ChessFigureType.BISHOP)
    boardResult[0][3] = ChessFigure(ChessPlayer.BLACK, ChessFigureType.QUEEN)
    boardResult[0][width - 4] = ChessFigure(ChessPlayer.BLACK, ChessFigureType.KING, true)

    boardResult[height - 1][0] = ChessFigure(ChessPlayer.WHITE, ChessFigureType.ROOK, true)
    boardResult[height - 1][width - 1] = ChessFigure(ChessPlayer.WHITE, ChessFigureType.ROOK, true)
    boardResult[height - 1][1] = ChessFigure(ChessPlayer.WHITE, ChessFigureType.KNIGHT)
    boardResult[height - 1][width - 2] = ChessFigure(ChessPlayer.WHITE, ChessFigureType.KNIGHT)
    boardResult[height - 1][2] = ChessFigure(ChessPlayer.WHITE, ChessFigureType.BISHOP)
    boardResult[height - 1][width - 3] = ChessFigure(ChessPlayer.WHITE, ChessFigureType.BISHOP)
    boardResult[height - 1][3] = ChessFigure(ChessPlayer.WHITE, ChessFigureType.QUEEN)
    boardResult[height - 1][width - 4] = ChessFigure(ChessPlayer.WHITE, ChessFigureType.KING, true)
    return boardResult
}
