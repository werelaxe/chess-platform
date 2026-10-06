package ru.werelaxe.chess.core

import kotlin.random.Random

sealed interface GameState {
    val sideToMove: Color
}

/** Rules of one chess variant operating on its own immutable state type. */
abstract class Variant<S : GameState> {
    abstract val kind: GameKind

    abstract fun initialState(): S

    /** Squares the piece on [from] may move to with a [GameMove.Normal] move. */
    abstract fun legalTargets(state: S, from: Square): Set<Square>

    /** Squares usable as the second target of a [GameMove.Split] once [first] was chosen. */
    abstract fun splitSecondTargets(state: S, from: Square, first: Square): Set<Square>

    /** Whether moving from [from] to [to] needs a promotion piece to be chosen. */
    abstract fun requiresPromotion(state: S, from: Square, to: Square): Boolean

    /** Whether a [GameMove.Observe] on [square] is a legal move. */
    abstract fun canObserve(state: S, square: Square): Boolean

    abstract fun isLegal(state: S, move: GameMove): Boolean

    /** Applies a legal move; throws [IllegalMoveException] otherwise. */
    abstract fun apply(state: S, move: GameMove): S

    abstract fun status(state: S): GameStatus

    abstract fun view(state: S): BoardView

    /**
     * Completes moves that need a random decision (observation outcomes). Must be called on
     * the authoritative side before [apply]; returns the move unchanged when nothing is needed.
     */
    open fun resolve(state: S, move: GameMove, random: Random): GameMove = move
}

object Variants {
    fun of(kind: GameKind): Variant<out GameState> = when (kind) {
        GameKind.CLASSIC -> ClassicVariant
        GameKind.QUANTUM -> QuantumVariant
    }
}
