package dialects.chess.classic

object ChessPlayer {
    const val WHITE = 1
    const val BLACK = 2

    fun another(player: Int) = if (player == WHITE) BLACK else WHITE
}
