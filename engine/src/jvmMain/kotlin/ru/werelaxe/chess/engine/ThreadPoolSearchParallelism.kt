package ru.werelaxe.chess.engine

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Spreads the items over the calling thread and up to as many threads of [executor] as
 * [helperPermits] has free at the moment, never more than there are other items: a search on
 * a lightly loaded machine takes the idle cores, one on a busy machine finds no helper and runs
 * on its own thread instead of oversubscribing. Helpers are borrowed per [map] call without
 * waiting and returned as soon as the items run out. An exception from any item (the search
 * timeout) is rethrown on the calling thread once every item that was started has finished;
 * the items not started yet are dropped.
 *
 * The threads of [executor] must not call [map] themselves: a helper never borrows helpers.
 */
class ThreadPoolSearchParallelism(private val executor: Executor, private val helperPermits: Semaphore) : SearchParallelism {
    override fun <T, R> map(items: List<T>, task: (T) -> R): List<R> {
        var helpers = 0
        while (helpers < items.size - 1 && helperPermits.tryAcquire()) helpers++
        if (helpers == 0) return items.map(task)

        val run = Run(items, task)
        val finished = CountDownLatch(helpers)
        repeat(helpers) {
            try {
                executor.execute {
                    try {
                        run.work()
                    } finally {
                        helperPermits.release()
                        finished.countDown()
                    }
                }
            } catch (e: RejectedExecutionException) {
                // The pool is shutting down; the calling thread does this helper's share.
                helperPermits.release()
                finished.countDown()
            }
        }
        run.work()
        finished.await()
        run.failure.get()?.let { throw it }
        return run.results()
    }

    override fun sharedBound(initial: Int): SharedBound = AtomicBound(initial)

    /** The items of one [map] call, handed out in order to whichever thread asks next. */
    private class Run<T, R>(private val items: List<T>, private val task: (T) -> R) {
        private val next = AtomicInteger()
        private val results = arrayOfNulls<Any?>(items.size)
        val failure = AtomicReference<Throwable?>()

        /** Runs items until they run out or one of them, on any thread, has failed. */
        fun work() {
            while (failure.get() == null) {
                val index = next.getAndIncrement()
                if (index >= items.size) return
                try {
                    results[index] = task(items[index])
                } catch (e: Throwable) {
                    failure.compareAndSet(null, e)
                    return
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun results(): List<R> = List(items.size) { results[it] as R }
    }

    private class AtomicBound(initial: Int) : SharedBound {
        private val value = AtomicInteger(initial)

        override fun get(): Int = value.get()

        override fun raise(value: Int) {
            this.value.accumulateAndGet(value) { current, candidate -> maxOf(current, candidate) }
        }
    }
}
