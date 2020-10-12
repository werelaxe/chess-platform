package dialects.chess.quantum

import core.Coordinate
import core.Result
import core.Rules
import dialects.chess.classic.ChessPlayer
import dialects.chess.classic.ChessRules
import kotlin.math.abs
import kotlin.math.sign


class QuantumChessRules: Rules<QuantumChessFigure, QuantumChessState> {
    private val classicRules = ChessRules()

    override fun nextPlayer(state: QuantumChessState, from: Coordinate, to: Coordinate) = ChessPlayer.another(state.currentPlayer)

    override fun isTerminateState(state: QuantumChessState): Boolean {
        println("Fix this terminal state")
        return false
    }

    override fun winners(state: QuantumChessState): Result {
        TODO("Not yet implemented")
    }

    override fun possibleSteps(state: QuantumChessState, from: Coordinate): List<Coordinate> {
        return state.states.map { classicRules.possibleSteps(it, from) }.flatten().distinct()
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
            if (classicRules.canMove(newState, from, to)) {
                newState.move(from, to)
            }
        }
    }

    override fun move(state: QuantumChessState, from: Coordinate, to: Coordinate) {
        state.states.forEach {
            if (classicRules.canMove(it, from, to)) {
                it.move(from, to)
            }
        }
    }

    override fun postMove(state: QuantumChessState, from: Coordinate, to: Coordinate) {
        state.states.forEach {
            classicRules.postMove(it, from, to)
        }
    }
}
