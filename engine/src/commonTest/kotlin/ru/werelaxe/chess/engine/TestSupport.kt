package ru.werelaxe.chess.engine

import ru.werelaxe.chess.core.Board
import ru.werelaxe.chess.core.Fen
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.GameStatus
import ru.werelaxe.chess.core.Move
import ru.werelaxe.chess.core.QuantumState
import ru.werelaxe.chess.core.QuantumVariant
import ru.werelaxe.chess.core.Square
import kotlin.random.Random

internal fun board(fen: String): Board = Fen.parse(fen).board

internal fun sq(name: String): Square = Square.parse(name)

internal fun normal(uci: String): GameMove.Normal = Move.parseUci(uci).let { GameMove.Normal(it.from, it.to, it.promotion) }

internal fun split(from: String, first: String, second: String): GameMove.Split = GameMove.Split(sq(from), sq(first), sq(second))

/** A busy middlegame position ("Kiwipete" from the perft suite). */
internal const val KIWIPETE = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"

/** An ongoing quantum position with at least [universes] universes, reached by random splits from the initial position. */
internal fun quantumPosition(universes: Int, seed: Int = 11): QuantumState {
    var state = QuantumState.initial()
    val random = Random(seed)
    while (state.universeCount < universes) {
        val mover = state.sideToMove
        val splits = ArrayList<GameMove>()
        for (from in Square.ALL) {
            if (state.universes.keys.none { it[from]?.color == mover }) continue
            val targets = QuantumVariant.legalTargets(state, from).toList()
            for (i in targets.indices) for (j in i + 1 until targets.size) {
                val move = GameMove.Split(from, targets[i], targets[j])
                if (QuantumVariant.isLegal(state, move)) splits.add(move)
            }
        }
        val next = QuantumVariant.apply(state, splits[random.nextInt(splits.size)])
        if (QuantumVariant.status(next) == GameStatus.Ongoing) state = next
    }
    return state
}
