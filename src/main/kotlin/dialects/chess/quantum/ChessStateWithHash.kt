package dialects.chess.quantum

import core.Coordinate
import dialects.chess.classic.ChessFigure
import dialects.chess.classic.ChessPlayer
import dialects.chess.classic.ChessState
import dialects.chess.classic.genStartBoard

class ChessStateWithHash(
    private val hashKeeper: HashKeeper,
    width: Int,
    height: Int,
    board: MutableList<MutableList<ChessFigure?>>,
    context: Context = Context(),
    startPlayer: Int = ChessPlayer.WHITE
): ChessState(width, height, board, context, startPlayer) {
    constructor(hashKeeper: HashKeeper, width: Int = 8, height: Int = 8):
        this(hashKeeper, width, height, genStartBoard(width, height))
    var boardHash = 0
        private set

    init {
        board.withIndex().forEach { (y, row) ->
            row.withIndex().forEach { (x, fig) ->
                boardHash = ((boardHash.toLong() + cellHash(Coordinate.of(x, y), fig).toLong()) % MAX_HASH_VAL).toInt()
            }
        }
        hashKeeper.add(boardHash)
    }

    override fun setEl(coord: Coordinate, figure: ChessFigure?) {
        var newHash = boardHash.toLong() - cellHash(coord, this[coord]).toLong()
        newHash += cellHash(coord, figure).toLong()
        hashKeeper.change(boardHash, newHash.toInt())
        boardHash = newHash.toInt()
        super.setEl(coord, figure)
    }

    override fun clone(): ChessStateWithHash {
        val boardClone = board.clone()
        hashKeeper
        return with(ChessStateWithHash(hashKeeper, width, height, boardClone, startPlayer = currentPlayer)) {
            this.enPassantPair = enPassantPair
            this.blackKingPosition = this@ChessStateWithHash.blackKingPosition
            this.whiteKingPosition = this@ChessStateWithHash.whiteKingPosition
            this
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is ChessStateWithHash) {
            return false
        }
        if (boardHash != other.boardHash) {
            return false
        }
        if (board != other.board) {
            return false
        }
        if (enPassantPair != other.enPassantPair) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        return boardHash
    }
}