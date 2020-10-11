package dialects.chess.classic

import core.Coordinate
import core.Result
import core.Rules


class ChessRules: Rules<ChessFigure, ChessState> {
    override fun nextPlayer(state: ChessState, from: Coordinate, to: Coordinate): Int {
        return if (state.currentPlayer == ChessPlayer.BLACK) ChessPlayer.WHITE else ChessPlayer.BLACK
    }

    private fun canCurrentPlayerMove(state: ChessState): Boolean {
        for (x in 0 until state.width) {
            for (y in 0 until state.height) {
                val coord = Coordinate.of(x, y)
                state[coord]?.let { fig ->
                    if (fig.owner == state.currentPlayer) {
                        if (possibleSteps(state, coord).isNotEmpty()) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    override fun isTerminateState(state: ChessState): Boolean {
        return winners(state).isOver
    }

    override fun winners(state: ChessState): Result {
        if (!canCurrentPlayerMove(state)) {
            return Result(true, setOf(ChessPlayer.another(state.currentPlayer)), setOf(state.currentPlayer))
        }
        return Result(false, emptySet(), emptySet())
    }

    private val knightDiffs = listOf(
        Coordinate.of(1, 2),
        Coordinate.of(1, -2),
        Coordinate.of(-1, 2),
        Coordinate.of(-1, -2),
        Coordinate.of(2, 1),
        Coordinate.of(-2, 1),
        Coordinate.of(2, -1),
        Coordinate.of(-2, -1),
    )

    private fun possibleStepsForRook(state: ChessState, from: Coordinate): List<Coordinate> {
        val res = mutableListOf<Coordinate>()
        for (rng in listOf((from.x() - 1) downTo 0, (from.x() + 1) until state.width)) {
            for (x in rng) {
                val coord = Coordinate.of(x, from.y())
                res.add(coord)
                if (state[coord] != null) {
                    break
                }
            }
        }
        for (rng in listOf((from.y() - 1) downTo 0, (from.y() + 1) until state.height)) {
            for (y in rng) {
                val coord = Coordinate.of(from.x(), y)
                res.add(coord)
                if (state[coord] != null) {
                    break
                }
            }
        }
        return res
    }

    private fun possibleStepsForBishop(state: ChessState, from: Coordinate): List<Coordinate> {
        val res = mutableListOf<Coordinate>()
        for (kx in listOf(-1, 1)) {
            for (ky in listOf(-1, 1)) {
                val diff = Coordinate.of(kx, ky)
                var coord = from + diff
                while (coord in state) {
                    res.add(coord)
                    if (state[coord] != null) {
                        break
                    }
                    coord += diff
                }
            }
        }
        return res
    }

    private fun possibleStepsForPawn(state: ChessState, from: Coordinate): List<Coordinate> {
        val playerCoef = playerCoef(state)
        val anotherPlayer = if (state.currentPlayer == ChessPlayer.BLACK) ChessPlayer.WHITE else ChessPlayer.BLACK
        val firstLine = if (state.currentPlayer == ChessPlayer.BLACK) 1 else state.height - 2

        val res = mutableListOf<Coordinate>()
        val next = from + Coordinate.of(0, 1) * playerCoef
        val nextNext = from + Coordinate.of(0, 2) * playerCoef
        if (state[next] == null) {
            res.add(next)
        }
        if (from.y() == firstLine && state[next] == null && state[nextNext] == null) {
            res.add(nextNext)
        }
        for (diff in listOf(-1, 1)) {
            val coord = from + Coordinate.of(diff, playerCoef)
            if (coord in state && state[coord]?.owner == anotherPlayer) {
                res.add(coord)
            }
        }
        return res
    }

    private fun playerCoef(state: ChessState) = if (state.currentPlayer == ChessPlayer.BLACK) 1 else -1

    private fun possibleStepsForKing(state: ChessState, from: Coordinate): List<Coordinate> {
        val res = mutableListOf<Coordinate>()
        for (dx in -1..1) {
            for (dy in -1..1) {
                val coord = Coordinate.of(from.x() + dx, from.y() + dy)
                if ((dx != 0 || dy != 0) && coord in state) {
                    res.add(coord)
                }
            }
        }
        return res
    }

    private fun possibleStepsForKnight(state: ChessState, from: Coordinate): List<Coordinate> {
        return knightDiffs
                .map { diff -> from + diff }
                .filter { it in state }
    }

    private val typesAndPossibleSteps = listOf(
        setOf(ChessFigureType.KNIGHT) to ::possibleStepsForKnight,
        setOf(ChessFigureType.ROOK, ChessFigureType.QUEEN) to ::possibleStepsForRook,
        setOf(ChessFigureType.BISHOP, ChessFigureType.QUEEN) to ::possibleStepsForBishop,
        setOf(ChessFigureType.KING) to ::possibleStepsForKing,
    )

    private fun isCheckByPawn(state: ChessState): Boolean {
        val playerCoef = playerCoef(state)
        for (k in listOf(-1, 1)) {
            state[state.kingPosition + Coordinate.of(k, playerCoef)]?.let { leftFig ->
                if (leftFig.owner == ChessPlayer.another(state.currentPlayer)) {
                    return true
                }
            }
        }
        return false
    }

    private fun isCheck(state: ChessState): Boolean {
        if (isCheckByPawn(state)) {
            return true
        }
        for ((types, possibleSteps) in typesAndPossibleSteps) {
            for (coord in possibleSteps(state, state.kingPosition)) {
                val fig = state[coord]
                if (fig?.figureType in types && fig?.owner == ChessPlayer.another(state.currentPlayer)) {
                    return true
                }
            }
        }
        return false
    }

    private fun isStepCheck(state: ChessState, from: Coordinate, to: Coordinate): Boolean {
        var result = false
        val backupToFig = state[to]
        state.move(from, to)
        if (isCheck(state)) {
            result = true
        }
        state.move(to, from)
        state[to] = backupToFig
        return result
    }

    fun possibleStepsIgnoreCheck(state: ChessState, from: Coordinate): List<Coordinate> {
        state[from]?.let { fromFig ->
            if (!isCurrentPlayerStep(state, fromFig)) {
                return emptyList()
            }
            val res = when (fromFig.figureType) {
                ChessFigureType.PAWN -> {
                    possibleStepsForPawn(state, from)
                }
                ChessFigureType.KNIGHT -> {
                    possibleStepsForKnight(state, from)
                }
                ChessFigureType.ROOK -> {
                    possibleStepsForRook(state, from)
                }
                ChessFigureType.KING -> {
                    possibleStepsForKing(state, from)
                }
                ChessFigureType.BISHOP -> {
                    possibleStepsForBishop(state, from)
                }
                ChessFigureType.QUEEN -> {
                    possibleStepsForRook(state, from) + possibleStepsForBishop(state, from)
                }
            }.filter { state[it]?.owner != state.currentPlayer }
            return res
        }
        return emptyList()
    }

    override fun possibleSteps(state: ChessState, from: Coordinate): List<Coordinate> {
        return possibleStepsIgnoreCheck(state, from).filter { coord -> !isStepCheck(state, from, coord) }
    }
}
