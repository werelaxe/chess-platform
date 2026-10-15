package ru.werelaxe.chess.core

/**
 * The distributions a quantum game has gone through, for the threefold repetition rule. A
 * persistent chain: every state keeps the history of its predecessor plus its own
 * distribution, so recording one costs nothing but a small node (no copying, however long the
 * game), and [count] walks the chain comparing cached hash codes before anything else. A
 * search applying thousands of moves to a long game therefore pays the same per move as in a
 * short one.
 */
class PositionHistory private constructor(
    private val previous: PositionHistory?,
    private val distribution: Map<Board, Long>?,
    private val hash: Int,
    /** Distributions recorded so far. */
    val size: Int,
) {
    /** This history with [distribution] recorded after everything so far. */
    fun plus(distribution: Map<Board, Long>): PositionHistory =
        PositionHistory(this, distribution, distribution.hashCode(), size + 1)

    /** How many times [distribution] has been recorded. */
    fun count(distribution: Map<Board, Long>): Int {
        val hash = distribution.hashCode()
        var count = 0
        var node: PositionHistory? = this
        while (node != null) {
            if (node.hash == hash && node.distribution == distribution) count++
            node = node.previous
        }
        return count
    }

    /** Every distribution recorded with how many times, in order of first occurrence; for tests and tools. */
    fun toMap(): Map<Map<Board, Long>, Int> {
        val nodes = ArrayList<Map<Board, Long>>(size)
        var node: PositionHistory? = this
        while (node != null) {
            node.distribution?.let { nodes.add(it) }
            node = node.previous
        }
        val counts = LinkedHashMap<Map<Board, Long>, Int>()
        for (distribution in nodes.asReversed()) counts[distribution] = (counts[distribution] ?: 0) + 1
        return counts
    }

    /** Two histories are equal when they recorded the same distributions in the same order. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PositionHistory || other.size != size) return false
        var a: PositionHistory? = this
        var b: PositionHistory? = other
        while (a != null && b != null) {
            if (a === b) return true
            if (a.hash != b.hash || a.distribution != b.distribution) return false
            a = a.previous
            b = b.previous
        }
        return a == null && b == null
    }

    override fun hashCode(): Int = hash * 31 + size

    companion object {
        /** No distribution recorded yet. */
        val EMPTY = PositionHistory(null, null, 0, 0)
    }
}
