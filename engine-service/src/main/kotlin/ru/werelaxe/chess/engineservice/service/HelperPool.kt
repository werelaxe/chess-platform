package ru.werelaxe.chess.engineservice.service

import ru.werelaxe.chess.engine.SearchParallelism
import ru.werelaxe.chess.engine.ThreadPoolSearchParallelism
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

/**
 * [size] daemon threads shared by all searches, each borrowing the idle ones for its root
 * moves or candidates through [parallelism]: a search on a quiet service spreads over them,
 * one on a busy service finds none free and stays on its own thread, so that never more than
 * the search threads plus [size] threads search at once. No helpers means sequential searches.
 */
class HelperPool(val size: Int) : AutoCloseable {
    private val permits = Semaphore(size)
    private val counter = AtomicInteger()
    private val threads: ExecutorService? = if (size == 0) null else Executors.newFixedThreadPool(size) { runnable ->
        Thread(runnable, "search-helper-${counter.incrementAndGet()}").apply { isDaemon = true }
    }

    val parallelism: SearchParallelism =
        if (threads == null) SearchParallelism.Sequential else ThreadPoolSearchParallelism(threads, permits)

    /** Helpers lent to running searches right now. */
    val busy: Int
        get() = size - permits.availablePermits()

    override fun close() {
        threads?.shutdown()
    }
}
