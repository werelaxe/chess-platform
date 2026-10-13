package ru.werelaxe.chess.engine

/**
 * How a search spreads its independent pieces of work, the root moves of a classic search and
 * the candidates refined by a quantum search, over threads. [Sequential] runs them one after
 * another on the calling thread; the JVM engine service plugs in a thread pool. The searches
 * are written so that any order of execution gives the same scores: only the timing and the
 * choice among equally scored moves may differ.
 */
interface SearchParallelism {
    /** Runs [task] on every item, possibly concurrently, and returns the results in item order. */
    fun <T, R> map(items: List<T>, task: (T) -> R): List<R>

    /**
     * A bound shared by the tasks of one [map] call: every task reads it when it starts and
     * raises it when it finds a better score. Thread-safe whenever [map] runs tasks concurrently.
     */
    fun sharedBound(initial: Int): SharedBound

    /** Everything on the calling thread, in order. */
    object Sequential : SearchParallelism {
        override fun <T, R> map(items: List<T>, task: (T) -> R): List<R> = items.map(task)

        override fun sharedBound(initial: Int): SharedBound = PlainBound(initial)
    }
}

/** An integer that only ever grows; see [SearchParallelism.sharedBound]. */
interface SharedBound {
    fun get(): Int

    /** Raises the bound to [value] unless it is already at least that. */
    fun raise(value: Int)
}

/** A plain variable, enough when the tasks sharing it run one after another. */
class PlainBound(private var value: Int) : SharedBound {
    override fun get(): Int = value

    override fun raise(value: Int) {
        if (value > this.value) this.value = value
    }
}
