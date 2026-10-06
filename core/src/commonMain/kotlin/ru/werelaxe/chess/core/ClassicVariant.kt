package ru.werelaxe.chess.core

/** Classic chess state: the position plus what is needed for the draw rules. */
class ClassicState internal constructor(
    val board: Board,
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
    /** How many times each position occurred, for the threefold repetition rule. */
    val positionCounts: Map<Board, Int>,
    val lastMove: Move?,
) : GameState {
    override val sideToMove: Color
        get() = board.sideToMove

    val fen: String
        get() = Fen.format(board, halfmoveClock, fullmoveNumber)

    companion object {
        fun initial(): ClassicState = fromFen(Fen.INITIAL)

        fun fromFen(fen: String): ClassicState {
            val position = Fen.parse(fen)
            return ClassicState(position.board, position.halfmoveClock, position.fullmoveNumber, mapOf(position.board to 1), null)
        }
    }
}

object ClassicVariant : Variant<ClassicState>() {
    override val kind: GameKind = GameKind.CLASSIC

    override fun initialState(): ClassicState = ClassicState.initial()

    override fun legalTargets(state: ClassicState, from: Square): Set<Square> =
        ClassicRules.legalMoves(state.board, from).mapTo(LinkedHashSet()) { it.to }

    override fun splitSecondTargets(state: ClassicState, from: Square, first: Square): Set<Square> = emptySet()

    override fun requiresPromotion(state: ClassicState, from: Square, to: Square): Boolean =
        ClassicRules.legalMoves(state.board, from).any { it.to == to && it.promotion != null }

    override fun canObserve(state: ClassicState, square: Square): Boolean = false

    override fun isLegal(state: ClassicState, move: GameMove): Boolean =
        move is GameMove.Normal && ClassicRules.isLegal(state.board, move.toMove())

    override fun apply(state: ClassicState, move: GameMove): ClassicState {
        if (move !is GameMove.Normal) throw IllegalMoveException("Classic chess supports only normal moves")
        val classicMove = move.toMove()
        if (!ClassicRules.isLegal(state.board, classicMove)) throw IllegalMoveException("Illegal move ${classicMove.uci}")

        val board = ClassicRules.apply(state.board, classicMove)
        val halfmoveClock = if (ClassicRules.isIrreversible(state.board, classicMove)) 0 else state.halfmoveClock + 1
        val fullmoveNumber = if (state.board.sideToMove == Color.BLACK) state.fullmoveNumber + 1 else state.fullmoveNumber
        val positionCounts = state.positionCounts + (board to (state.positionCounts[board] ?: 0) + 1)
        return ClassicState(board, halfmoveClock, fullmoveNumber, positionCounts, classicMove)
    }

    override fun status(state: ClassicState): GameStatus {
        val board = state.board
        val side = board.sideToMove
        if (!ClassicRules.hasLegalMoves(board)) {
            return if (ClassicRules.isInCheck(board, side)) {
                GameStatus.Finished(side.opposite, EndReason.CHECKMATE)
            } else {
                GameStatus.Finished(null, EndReason.STALEMATE)
            }
        }
        if (ClassicRules.isInsufficientMaterial(board)) return GameStatus.Finished(null, EndReason.INSUFFICIENT_MATERIAL)
        if (state.halfmoveClock >= 100) return GameStatus.Finished(null, EndReason.FIFTY_MOVE_RULE)
        if ((state.positionCounts[board] ?: 0) >= 3) return GameStatus.Finished(null, EndReason.THREEFOLD_REPETITION)
        return GameStatus.Ongoing
    }

    override fun view(state: ClassicState): BoardView {
        val cells = Square.ALL.map { square ->
            val piece = state.board[square]
            CellView(square, if (piece == null) emptyList() else listOf(CellEntry(piece, 1.0)))
        }
        val check = ClassicRules.isInCheck(state.board, state.board.sideToMove)
        val lastMove = state.lastMove?.let { GameMove.Normal(it.from, it.to, it.promotion) }
        return BoardView(state.board.sideToMove, cells, 1, if (check) 1.0 else 0.0, lastMove)
    }
}
