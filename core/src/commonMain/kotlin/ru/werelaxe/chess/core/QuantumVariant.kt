package ru.werelaxe.chess.core

import kotlin.random.Random

/**
 * Quantum chess state: a weighted set of classical universes. Weights are relative
 * (probability = weight / total) and kept normalized by their greatest common divisor,
 * so two states with the same distribution are equal. All universes share the side to move.
 */
class QuantumState internal constructor(
    val universes: Map<Board, Long>,
    /** Plies since the last move that captured or moved a pawn in at least one universe. */
    val halfmoveClock: Int,
    /** How many times each distribution occurred, for the threefold repetition rule. */
    val positionCounts: Map<Map<Board, Long>, Int>,
    val lastMove: GameMove?,
) : GameState {
    init {
        require(universes.isNotEmpty()) { "A quantum state needs at least one universe" }
    }

    override val sideToMove: Color
        get() = universes.keys.first().sideToMove

    val totalWeight: Long = universes.values.sum()

    val universeCount: Int
        get() = universes.size

    /** Probability of each possible content of [square]; a null key means "empty". */
    fun distribution(square: Square): Map<Piece?, Double> {
        val weights = LinkedHashMap<Piece?, Long>()
        for ((board, weight) in universes) {
            val piece = board[square]
            weights[piece] = (weights[piece] ?: 0L) + weight
        }
        return weights.mapValues { it.value.toDouble() / totalWeight }
    }

    /** Total probability of universes satisfying [predicate]. */
    fun probability(predicate: (Board) -> Boolean): Double {
        var sum = 0L
        for ((board, weight) in universes) if (predicate(board)) sum += weight
        return sum.toDouble() / totalWeight
    }

    fun hasKingAnywhere(color: Color): Boolean = universes.keys.any { it.hasKing(color) }

    companion object {
        fun initial(): QuantumState = of(Board.initial())

        fun of(board: Board): QuantumState {
            val universes = mapOf(board to 1L)
            return QuantumState(universes, 0, mapOf(universes to 1), null)
        }
    }
}

object QuantumVariant : Variant<QuantumState>() {
    override val kind: GameKind = GameKind.QUANTUM

    /**
     * The total weight is kept at or below 2^[MAX_TOTAL_WEIGHT_BITS] so that a split (which doubles
     * it) can never overflow a Long. Above the bound, universes with probability below
     * 2^-[PRUNE_RATIO_BITS] are dropped first, then all weights are scaled down with rounding.
     */
    private const val MAX_TOTAL_WEIGHT_BITS = 50
    private const val MAX_TOTAL_WEIGHT = 1L shl MAX_TOTAL_WEIGHT_BITS
    private const val PRUNE_RATIO_BITS = 40

    override fun initialState(): QuantumState = QuantumState.initial()

    override fun legalTargets(state: QuantumState, from: Square): Set<Square> {
        val targets = LinkedHashSet<Square>()
        for (board in state.universes.keys) {
            for (move in ClassicRules.legalMoves(board, from)) targets.add(move.to)
        }
        return targets
    }

    override fun splitSecondTargets(state: QuantumState, from: Square, first: Square): Set<Square> {
        val targets = legalTargets(state, from)
        if (first == from || first !in targets) return emptySet()
        val result = LinkedHashSet<Square>()
        result.add(from)
        for (target in targets) if (target != first) result.add(target)
        return result
    }

    override fun requiresPromotion(state: QuantumState, from: Square, to: Square): Boolean =
        state.universes.keys.any { board ->
            ClassicRules.legalMoves(board, from).any { it.to == to && it.promotion != null }
        }

    override fun canObserve(state: QuantumState, square: Square): Boolean =
        state.distribution(square).size >= 2

    override fun isLegal(state: QuantumState, move: GameMove): Boolean = when (move) {
        is GameMove.Normal -> state.universes.keys.any { ClassicRules.isLegal(it, resolveMove(it, move.from, move.to, move.promotion)) }
        is GameMove.Split -> isLegalSplit(state, move)
        is GameMove.Observe -> move.outcome != null && isPossibleOutcome(state, move.square, move.outcome.piece)
    }

    private fun isLegalSplit(state: QuantumState, move: GameMove.Split): Boolean {
        if (move.first == move.from || move.first == move.second) return false
        val stay = move.second == move.from
        var firstLegalSomewhere = false
        var secondLegalSomewhere = stay
        for (board in state.universes.keys) {
            if (!firstLegalSomewhere && ClassicRules.isLegal(board, resolveMove(board, move.from, move.first, move.promotion))) {
                firstLegalSomewhere = true
            }
            if (!secondLegalSomewhere && ClassicRules.isLegal(board, resolveMove(board, move.from, move.second, move.promotion))) {
                secondLegalSomewhere = true
            }
            if (firstLegalSomewhere && secondLegalSomewhere) return true
        }
        return false
    }

    private fun isPossibleOutcome(state: QuantumState, square: Square, piece: Piece?): Boolean =
        canObserve(state, square) && state.universes.keys.any { it[square] == piece }

    /** The promotion piece only applies to pawns reaching the last rank in this particular universe. */
    private fun resolveMove(board: Board, from: Square, to: Square, promotion: PieceType?): Move {
        val piece = board[from]
        val promotes = piece != null && piece.type == PieceType.PAWN && to.rank == piece.color.promotionRank
        return Move(from, to, if (promotes) promotion else null)
    }

    /**
     * Samples a fresh outcome for an observation. Any outcome already present in [move] is
     * ignored on purpose: the authoritative side must never let a client choose how a square
     * collapses. Replaying a stored history goes through [apply], which honours stored outcomes.
     */
    override fun resolve(state: QuantumState, move: GameMove, random: Random): GameMove {
        if (move !is GameMove.Observe) return move
        if (!canObserve(state, move.square)) return move
        return move.copy(outcome = Observation(sampleContent(state, move.square, random)))
    }

    /** Samples the content of [square] from the universe distribution. */
    fun sampleContent(state: QuantumState, square: Square, random: Random): Piece? {
        check(state.totalWeight > 0) { "Corrupt quantum state: total weight ${state.totalWeight}" }
        var remaining = random.nextLong(state.totalWeight)
        for ((board, weight) in state.universes) {
            if (remaining < weight) return board[square]
            remaining -= weight
        }
        return state.universes.keys.last()[square]
    }

    override fun apply(state: QuantumState, move: GameMove): QuantumState {
        val accumulator = Accumulator()
        when (move) {
            is GameMove.Normal -> applyNormal(state, move, accumulator)
            is GameMove.Split -> applySplit(state, move, accumulator)
            is GameMove.Observe -> applyObserve(state, move, accumulator)
        }
        val universes = normalize(accumulator.universes)
        val halfmoveClock = if (accumulator.irreversible) 0 else state.halfmoveClock + 1
        val positionCounts = state.positionCounts + (universes to (state.positionCounts[universes] ?: 0) + 1)
        return QuantumState(universes, halfmoveClock, positionCounts, move)
    }

    private class Accumulator {
        val universes = LinkedHashMap<Board, Long>()
        var irreversible = false

        fun add(board: Board, weight: Long) {
            universes[board] = (universes[board] ?: 0L) + weight
        }

        fun addApplied(board: Board, move: Move, weight: Long) {
            if (ClassicRules.isIrreversible(board, move)) irreversible = true
            add(ClassicRules.apply(board, move), weight)
        }
    }

    private fun applyNormal(state: QuantumState, move: GameMove.Normal, out: Accumulator) {
        var applied = false
        for ((board, weight) in state.universes) {
            val classicMove = resolveMove(board, move.from, move.to, move.promotion)
            if (ClassicRules.isLegal(board, classicMove)) {
                out.addApplied(board, classicMove, weight)
                applied = true
            } else {
                out.add(ClassicRules.pass(board), weight)
            }
        }
        if (!applied) throw IllegalMoveException("Move ${move.from}${move.to} is not legal in any universe")
    }

    private fun applySplit(state: QuantumState, move: GameMove.Split, out: Accumulator) {
        if (!isLegalSplit(state, move)) throw IllegalMoveException("Split ${move.from}->${move.first}/${move.second} is not legal")
        val stay = move.second == move.from
        for ((board, weight) in state.universes) {
            val first = resolveMove(board, move.from, move.first, move.promotion)
            val second = resolveMove(board, move.from, move.second, move.promotion)
            val firstLegal = ClassicRules.isLegal(board, first)
            val secondLegal = !stay && ClassicRules.isLegal(board, second)
            when {
                firstLegal && (secondLegal || stay) -> {
                    out.addApplied(board, first, weight)
                    if (stay) out.add(ClassicRules.pass(board), weight) else out.addApplied(board, second, weight)
                }
                firstLegal -> out.addApplied(board, first, 2 * weight)
                secondLegal -> out.addApplied(board, second, 2 * weight)
                else -> out.add(ClassicRules.pass(board), 2 * weight)
            }
        }
    }

    private fun applyObserve(state: QuantumState, move: GameMove.Observe, out: Accumulator) {
        val outcome = move.outcome ?: throw IllegalMoveException("Observation outcome has not been decided")
        if (!canObserve(state, move.square)) throw IllegalMoveException("Square ${move.square} has nothing to observe")
        for ((board, weight) in state.universes) {
            if (board[move.square] == outcome.piece) out.add(ClassicRules.pass(board), weight)
        }
        if (out.universes.isEmpty()) throw IllegalMoveException("Outcome ${outcome.piece} on ${move.square} is impossible")
    }

    private fun normalize(universes: LinkedHashMap<Board, Long>): Map<Board, Long> {
        var result: MutableMap<Board, Long> = universes
        divideByGcd(result)
        var total = result.values.sum()
        if (total > MAX_TOTAL_WEIGHT) {
            val minimum = total shr PRUNE_RATIO_BITS
            result = result.filterTo(LinkedHashMap()) { it.value >= minimum }
            total = result.values.sum()
            if (total > MAX_TOTAL_WEIGHT) {
                // Scale down with rounding; the relative error is below 2^-49 and every universe survives.
                val shift = (64 - total.countLeadingZeroBits()) - MAX_TOTAL_WEIGHT_BITS
                val half = 1L shl (shift - 1)
                for (entry in result.entries) entry.setValue(maxOf(1L, (entry.value + half) shr shift))
            }
            divideByGcd(result)
        }
        return result
    }

    private fun divideByGcd(universes: MutableMap<Board, Long>) {
        var gcd = 0L
        for (weight in universes.values) gcd = gcd(gcd, weight)
        if (gcd > 1) {
            for (entry in universes.entries) entry.setValue(entry.value / gcd)
        }
    }

    private fun gcd(a: Long, b: Long): Long {
        var x = a
        var y = b
        while (y != 0L) {
            val t = x % y
            x = y
            y = t
        }
        return x
    }

    override fun status(state: QuantumState): GameStatus {
        val side = state.sideToMove
        if (!state.hasKingAnywhere(side)) return GameStatus.Finished(side.opposite, EndReason.KING_CAPTURED)
        if (!state.hasKingAnywhere(side.opposite)) return GameStatus.Finished(side, EndReason.KING_CAPTURED)

        val canMove = state.universes.keys.any { ClassicRules.hasLegalMoves(it) }
        if (!canMove) {
            val checkedSomewhere = state.universes.keys.any { ClassicRules.isInCheck(it, side) }
            return if (checkedSomewhere) {
                GameStatus.Finished(side.opposite, EndReason.CHECKMATE)
            } else {
                GameStatus.Finished(null, EndReason.STALEMATE)
            }
        }
        if (state.halfmoveClock >= 100) return GameStatus.Finished(null, EndReason.FIFTY_MOVE_RULE)
        if ((state.positionCounts[state.universes] ?: 0) >= 3) return GameStatus.Finished(null, EndReason.THREEFOLD_REPETITION)
        return GameStatus.Ongoing
    }

    /** Most likely first; on ties pieces precede "empty" and white precedes black, so views are deterministic. */
    private val CELL_ENTRY_ORDER: Comparator<CellEntry> = compareByDescending<CellEntry> { it.probability }
        .thenBy { if (it.piece == null) 1 else 0 }
        .thenBy { it.piece?.color?.ordinal ?: 0 }
        .thenBy { it.piece?.type?.ordinal ?: 0 }

    override fun view(state: QuantumState): BoardView {
        val cells = Square.ALL.map { square ->
            val distribution = state.distribution(square)
            val entries = distribution.entries
                .filter { it.key != null || it.value < 1.0 }
                .map { CellEntry(it.key, it.value) }
                .sortedWith(CELL_ENTRY_ORDER)
            CellView(square, entries)
        }
        val check = state.probability { ClassicRules.isInCheck(it, it.sideToMove) }
        return BoardView(state.sideToMove, cells, state.universeCount, check, state.lastMove)
    }
}
