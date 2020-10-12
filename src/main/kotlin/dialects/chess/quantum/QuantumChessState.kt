package dialects.chess.quantum

import core.Coordinate
import core.State
import dialects.chess.classic.ChessFigure
import dialects.chess.classic.ChessPlayer
import dialects.chess.classic.ChessState


class QuantumChessState: State<QuantumChessFigure>(ChessPlayer.WHITE) {
    val width = 8
    val height = 8

    val states = mutableListOf(ChessState())

    override fun getEl(coord: Coordinate): QuantumChessFigure? {
        val distribution = states.mapNotNull { it[coord] }
            .groupingBy { it }
            .eachCount()
            .map { QuantumFigurePair(it.value.toDouble() / states.size, it.key) }
        return QuantumChessFigure(distribution)
    }

    fun getEl(coord: Coordinate, stateIndex: Int): ChessFigure? = states[stateIndex][coord]

    override fun setEl(coord: Coordinate, figure: QuantumChessFigure?) {}

    fun setEl(coord: Coordinate, stateIndex: Int, figure: ChessFigure?) {
        states[stateIndex][coord] = figure
    }

    override fun contains(coord: Coordinate) = coord.x() in 0 until width && coord.y() in 0 until height

    override fun move(from: Coordinate, to: Coordinate) {}
}
