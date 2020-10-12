package dialects.chess.quantum

import core.AdditionalStepInfo
import core.Coordinate
import core.Game
import dialects.GameKind


class QuantumChessGame: Game<QuantumChessFigure, QuantumChessState, QuantumChessRules>(
        GameKind.QUANTUM_CHESS,
        QuantumChessState(),
        QuantumChessRules()
) {
    private fun AdditionalStepInfo.isQuantum() = records["is_quantum"]?.toBoolean()

    override fun step(from: Coordinate, to: Coordinate, additionalStepInfo: AdditionalStepInfo?) {
        preStepCheck(from, to)

        rules.preMove(state, from, to)
        if (additionalStepInfo?.isQuantum() == true && state.context.isQuantumMove == null) {
            state.context.isQuantumMove = from
            rules.quantumMove(state, from, to)
        } else {
            rules.move(state, from, to)
            state.currentPlayer = rules.nextPlayer(state, from, to)
            state.states.forEach {
                it.currentPlayer = state.currentPlayer
            }
            state.context.isQuantumMove = null
        }
        rules.postMove(state, from, to)
    }

    companion object {
        fun createQuantumChessGame() = QuantumChessGame()
    }
}
