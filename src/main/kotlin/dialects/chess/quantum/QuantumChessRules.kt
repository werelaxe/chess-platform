package dialects.chess.quantum

import core.AdditionalStepInfo
import core.Coordinate
import core.Result
import core.Rules
import dialects.chess.classic.ChessFigure
import dialects.chess.classic.ChessFigureType
import dialects.chess.classic.ChessPlayer
import dialects.chess.classic.ChessRules
import kotlin.random.Random


fun AdditionalStepInfo.isQuantum() = records["is_quantum"]?.toBoolean()
fun AdditionalStepInfo.isObservation() = records["is_observation"]?.toBoolean()


class QuantumChessRules: Rules<QuantumChessFigure, QuantumChessState> {
    private val classicRules = ChessRules()

    override fun nextPlayer(state: QuantumChessState, from: Coordinate, to: Coordinate) = ChessPlayer.another(state.currentPlayer)

    override fun isTerminateState(state: QuantumChessState): Boolean {
        return state.blackKingCount() == 0 || state.whiteKingCount() == 0 || !canCurrentPlayerMove(state)
    }

    fun canCurrentPlayerMove(state: QuantumChessState) = state.states.any { classicRules.canCurrentPlayerMove(it) }

    override fun winners(state: QuantumChessState) = when {
        !canCurrentPlayerMove(state) -> Result(true, setOf(ChessPlayer.another(state.currentPlayer)), setOf(state.currentPlayer))
        state.whiteKingCount() == 0 -> Result(true, setOf(ChessPlayer.BLACK), setOf(ChessPlayer.WHITE))
        state.blackKingCount() == 0 -> Result(true, setOf(ChessPlayer.WHITE), setOf(ChessPlayer.BLACK))
        else -> Result(false, emptySet(), emptySet())
    }

    override fun possibleSteps(state: QuantumChessState, from: Coordinate): List<Coordinate> {
        state.context.isQuantumMove?.let { qm ->
            if (from != qm) {
                return emptyList()
            }
            return (state.states.map { classicRules.possibleSteps(it, from) }.flatten() + listOf(from)).distinct()
        }
        return (state.states.map { classicRules.possibleSteps(it, from) }.flatten()).distinct()
    }

    override fun isCurrentPlayerStep(state: QuantumChessState, figure: QuantumChessFigure): Boolean {
        return figure.figures.any { it.figure.owner == state.currentPlayer }
    }

    override fun preMove(state: QuantumChessState, from: Coordinate, to: Coordinate) {
        state.states.forEach {
            classicRules.preMove(it, from, to)
        }
    }

    fun quantumMove(state: QuantumChessState, from: Coordinate, to: Coordinate) {
        val sc = state.states.size
        for (i in 0 until sc) {
            val newState = state.states[i].clone()
            state.states.add(newState)
            if (classicRules.canMove(newState, from, to, )) {
                newState.move(from, to)
            }
        }
    }

    fun observe(state: QuantumChessState, coordinate: Coordinate) {
        state[coordinate]?.let { fig ->
            var resultFig: ChessFigure? = null

            val x = Random.nextDouble()
            var current = 0.0
            fig.figures.forEach { pair ->
                if (x in current..(current + pair.probability)) {
                    resultFig = pair.figure
                }
                current += pair.probability
            }

            state.states.removeAll { it[coordinate] != resultFig }
        }
    }

    override fun canMove(state: QuantumChessState, from: Coordinate, to: Coordinate, info: AdditionalStepInfo?): Boolean {
        if (to in state && info?.isObservation() == true) {
            return true
        }
        return super.canMove(state, from, to, info)
    }

    override fun move(state: QuantumChessState, from: Coordinate, to: Coordinate) {
        state.states.forEach {
            if (classicRules.canMove(it, from, to)) {
                val toFig = it[to]
                if (toFig?.figureType == ChessFigureType.KING) {
                    if (toFig.owner == ChessPlayer.WHITE) {
                        it.whiteKingPosition = voidPosition
                    } else {
                        it.blackKingPosition = voidPosition
                    }
                }
                it.move(from, to)
            }
        }
    }

    override fun postMove(state: QuantumChessState, from: Coordinate, to: Coordinate) {
        state.states.forEach {
            classicRules.postMove(it, from, to)
        }
    }

    companion object {
        val voidPosition = Coordinate.of(-100, -100)
    }
}
