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

    private fun canCurrentPlayerMove(state: QuantumChessState) = state.states.any { classicRules.canCurrentPlayerMove(it) }

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

    fun preMove(state: QuantumChessState, from: Coordinate, to: Coordinate) {
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
                classicRules.move(newState, from, to)
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

            state.states.removeAll { stateWithHash ->
                (stateWithHash[coordinate] != resultFig).apply {
                    if (this) {
                        state.hashKeeper.remove(stateWithHash.boardHash)
                    }
                }
            }
        }
    }

    override fun canMove(state: QuantumChessState, from: Coordinate, to: Coordinate, info: AdditionalStepInfo?): Boolean {
        if (to in state && info?.isObservation() == true) {
            return true
        }
        return super.canMove(state, from, to, info)
    }

    override fun move(state: QuantumChessState, from: Coordinate, to: Coordinate) {
        preMove(state, from, to)

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

        postMove(state, from, to)
    }

    fun postMove(state: QuantumChessState, from: Coordinate, to: Coordinate) {
        state.states.forEach {
            classicRules.postMove(it, from, to)
        }
    }

    fun isCollapsable(state: QuantumChessState): Boolean {
        if (!state.hashKeeper.isCollapsable()) {
            return false
        }
        val stateCheck = mutableMapOf<HashType, ChessStateWithHash?>()

        state.states.forEach { subState ->
            if (subState.boardHash !in stateCheck) {
                stateCheck[subState.boardHash] = subState
            }
            stateCheck[subState.boardHash]?.let { chessStateWithHash ->
                if (chessStateWithHash != subState) {
                    return false
                }
            }
        }
        return true
    }

    fun collapse(state: QuantumChessState) {
        val rememberedStates = mutableSetOf<ChessStateWithHash>()

        state.states.removeIf { chessStateWithHash ->
            if (chessStateWithHash !in rememberedStates) {
                rememberedStates.add(chessStateWithHash)
                false
            } else {
                state.hashKeeper.remove(chessStateWithHash.boardHash)
                true
            }
        }
    }

    companion object {
        val voidPosition = Coordinate.of(-100, -100)
    }
}
