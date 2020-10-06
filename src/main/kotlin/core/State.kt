package core

abstract class State<FigureType: Figure>(
    startPlayer: Int
) {
    abstract operator fun get(coord: Coordinate): FigureType?
    abstract operator fun set(coord: Coordinate, figure: FigureType?)
    abstract fun full(): Map<Coordinate, FigureType?>

    var currentPlayer: Int = startPlayer

    fun move(from: Coordinate, to: Coordinate) {
        this[to] = this[from]
        this[from] = null
    }
}
