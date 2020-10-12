package dialects.chess.quantum

import core.AdditionalStepInfo
import core.Coordinate
import core.Game
import dialects.GameKind


class QuantumChessGame: Game<QuantumChessFigure, QuantumChessState, QuantumChessRules>(
        GameKind.CLASSIC_CHESS,
        QuantumChessState(),
        QuantumChessRules()
) {
    private fun AdditionalStepInfo.isQuantum() = records["is_quantum"]?.toBoolean()

    override fun step(from: Coordinate, to: Coordinate, additionalStepInfo: AdditionalStepInfo?) {
        preStepCheck(from, to)

        rules.preMove(state, from, to)
        if (additionalStepInfo?.isQuantum() == true) {
            rules.quantumMove(state, from, to)
        } else {
            rules.move(state, from, to)
        }
        rules.postMove(state, from, to)

        state.currentPlayer = rules.nextPlayer(state, from, to)

    }
}
