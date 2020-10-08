package core

abstract class State<FigureType: Figure>(
    startPlayer: Int
) {
    abstract fun getEl(coord: Coordinate): FigureType?
    abstract fun setEl(coord: Coordinate, figure: FigureType?)
    abstract operator fun contains(coord: Coordinate): Boolean

    operator fun get(coord: Coordinate) = if (coord in this) getEl(coord) else null
    operator fun set(coord: Coordinate, figure: FigureType?) = setEl(coord, figure)

    var currentPlayer: Int = startPlayer

    fun move(from: Coordinate, to: Coordinate) {
        this[to] = this[from]
        this[from] = null
    }
}
