package ru.werelaxe.chess.core

import kotlin.random.Random

/**
 * A game of any variant: the current state plus the move history. The state is never
 * serialized; it is always reconstructed by replaying the moves, which keeps the server,
 * the web client and future mobile clients in exact agreement.
 */
class Game private constructor(
    val kind: GameKind,
    private val variant: Variant<GameState>,
) {
    @Suppress("UNCHECKED_CAST")
    constructor(kind: GameKind) : this(kind, Variants.of(kind) as Variant<GameState>)

    var state: GameState = variant.initialState()
        private set

    private val moves = ArrayList<GameMove>()

    /** A snapshot of the moves played so far. */
    val history: List<GameMove>
        get() = moves.toList()

    val moveCount: Int
        get() = moves.size

    val sideToMove: Color
        get() = state.sideToMove

    private var cachedStatus: GameStatus? = null

    fun status(): GameStatus = cachedStatus ?: variant.status(state).also { cachedStatus = it }

    val isOver: Boolean
        get() = status().isOver

    fun view(): BoardView = variant.view(state)

    /** Query helpers return nothing playable once the game is over, consistently with [isLegal]. */
    fun legalTargets(from: Square): Set<Square> = if (isOver) emptySet() else variant.legalTargets(state, from)

    fun splitSecondTargets(from: Square, first: Square): Set<Square> =
        if (isOver) emptySet() else variant.splitSecondTargets(state, from, first)

    fun requiresPromotion(from: Square, to: Square): Boolean = !isOver && variant.requiresPromotion(state, from, to)

    fun canObserve(square: Square): Boolean = !isOver && variant.canObserve(state, square)

    fun isLegal(move: GameMove): Boolean = !isOver && variant.isLegal(state, move)

    /** Fills in random outcomes (observations); call on the authoritative side before [apply]. */
    fun resolve(move: GameMove, random: Random = Random.Default): GameMove = variant.resolve(state, move, random)

    /** Applies a move; throws [IllegalMoveException] if it is illegal or the game is over. */
    fun apply(move: GameMove) {
        if (isOver) throw IllegalMoveException("The game is over")
        state = variant.apply(state, move)
        moves.add(move)
        cachedStatus = null
    }

    companion object {
        fun replay(kind: GameKind, moves: List<GameMove>): Game {
            val game = Game(kind)
            for (move in moves) game.apply(move)
            return game
        }
    }
}
