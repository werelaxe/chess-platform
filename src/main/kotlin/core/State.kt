package core

abstract class State<FigureType, Player>(
    startPlayer: Player
) {
    abstract operator fun get(coord: Coordinate): Figure<FigureType, Player>?
    abstract operator fun set(coord: Coordinate, figure: Figure<FigureType, Player>?)
    var currentPlayer: Player = startPlayer

    fun move(from: Coordinate, to: Coordinate) {
        this[to] = this[from]
        this[from] = null
    }

    fun ro() = ReadOnlyState(this)

    class ReadOnlyState<FigureType, Player, StateType : State<FigureType, Player>>(
            private val state: StateType
    ) {
        operator fun get(coord: Coordinate): Figure<FigureType, Player>? = state[coord]

        override fun toString() = state.toString()
    }
}
