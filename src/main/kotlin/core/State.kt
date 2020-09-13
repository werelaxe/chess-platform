package core

abstract class State(
    startPlayer: Player
) {
    abstract operator fun get(coord: Coordinate): Figure?
    abstract operator fun set(coord: Coordinate, figure: Figure?)
    abstract fun full(): Map<Coordinate, Figure?>

    var currentPlayer: Player = startPlayer

    fun move(from: Coordinate, to: Coordinate) {
        this[to] = this[from]
        this[from] = null
    }

    fun ro() = ReadOnlyState(this)

    class ReadOnlyState(
            private val state: State
    ) {
        operator fun get(coord: Coordinate): Figure? = state[coord]
        fun full() = state.full()
        override fun toString() = state.toString()
    }
}
