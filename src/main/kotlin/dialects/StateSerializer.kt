package dialects

import core.Figure
import core.Game
import core.State
import dialects.checkers.CheckersFigure
import dialects.checkers.CheckersGame
import dialects.checkers.CheckersState
import dialects.checkers.CheckersStateSerializer
import dialects.simple.SimpleFigure
import dialects.simple.SimpleGame
import dialects.simple.SimpleState
import dialects.simple.SimpleStateSerializer

abstract class StateSerializer <FigureType: Figure, StateType: State<FigureType>, GameType: Game<FigureType, StateType>> {
    private val figure2Id = mutableMapOf<FigureType, Int>()
    private val id2Figure = mutableMapOf<Int, FigureType>()

    protected fun getFigureId(figure: FigureType): Int {
        if (figure !in figure2Id) {
            figure2Id[figure] = figure2Id.size
            id2Figure[figure2Id[figure]!!] = figure
        }
        return figure2Id[figure]!!
    }

    abstract fun serialize(state: StateType): List<List<Int?>>

    private fun FigureType.serialize() = SerializableFigure(type, owner)

    fun figures(ids: List<Int>): List<SerializableFigure> =
        ids.map { id ->
            (id2Figure[id] ?: throw IllegalArgumentException("Invalid id '${id}'")).serialize()
        }

    companion object {
        private val kind2figures = mutableMapOf<GameKind, (List<Int>) -> List<SerializableFigure>>()
        private val kind2serialize = mutableMapOf<GameKind, (State<*>) -> List<List<Int?>>>()

        fun <FigureType: Figure, StateType: State<FigureType>, GameType: Game<FigureType, StateType>>
                register(kind: GameKind, serialize: (State<*>) -> List<List<Int?>>, figures: (List<Int>) -> List<SerializableFigure>) {
            kind2figures[kind] = figures
            kind2serialize[kind] = serialize
        }

        fun <FigureType: Figure, StateType: State<FigureType>, GameType: Game<FigureType, StateType>> serialize(game: GameType): List<List<Int?>> {
            val serialize = kind2serialize[game.kind] ?: throw Exception("Unknown kind: ${game.kind}")
            return serialize(game.state)
        }

        fun figures(kind: GameKind, ids: List<Int>): List<SerializableFigure> {
            val figures = kind2figures[kind] ?: throw Exception("Unknown kind: $kind")
            return figures(ids)
        }

        init {
            register<SimpleFigure, SimpleState, SimpleGame>(
                GameKind.SIMPLE,
                { state -> SimpleStateSerializer.serialize(state as SimpleState) },
                { ids -> SimpleStateSerializer.figures(ids) }
            )

            register<CheckersFigure, CheckersState, CheckersGame>(
                GameKind.CHECKERS,
                { state -> CheckersStateSerializer.serialize(state as CheckersState) },
                { ids -> CheckersStateSerializer.figures(ids) }
            )
        }
    }
}
