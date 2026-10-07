package ru.werelaxe.chess.server.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.werelaxe.chess.core.Game
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Replayed [Game] instances keyed by game id, each guarded by its own mutex so that
 * every mutation of a game (moves, joining, resigning, draw offers) is serialized.
 *
 * The cache is bounded two ways: beyond [maxEntries] ids the least recently used entries are
 * dropped, and an entry nobody has touched for [idleTimeout] is dropped as well, on the next
 * access (the oldest entries are checked) or by [sweep], which the application calls every
 * [SWEEP_INTERVAL]. An entry some coroutine is using is never dropped. A dropped game is
 * replayed from its moves when needed again. [timeSource] measures the idle time; tests inject
 * a controllable one.
 */
class GameCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val idleTimeout: Duration = DEFAULT_IDLE_TIMEOUT,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    class Entry internal constructor(internal var lastUsed: TimeMark) {
        val mutex = Mutex()

        /** Only read or written while holding [mutex]. */
        var game: Game? = null

        /** Coroutines inside [locked] for this entry (holding or waiting for [mutex]); guarded by the map. */
        internal var users = 0
    }

    /** Access-ordered, so iteration starts at the least recently used entry; guarded by itself. */
    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)

    val size: Int
        get() = synchronized(entries) { entries.size }

    suspend fun <T> locked(id: String, block: suspend (Entry) -> T): T {
        val entry = synchronized(entries) {
            val entry = entries.getOrPut(id) { Entry(timeSource.markNow()) }
            entry.users++
            entry.lastUsed = timeSource.markNow()
            evict()
            entry
        }
        try {
            return entry.mutex.withLock { block(entry) }
        } finally {
            synchronized(entries) {
                entry.users--
                entry.lastUsed = timeSource.markNow()
            }
        }
    }

    /** Drops every unused entry that has been idle for [idleTimeout]; returns how many were dropped. */
    fun sweep(): Int = synchronized(entries) {
        var dropped = 0
        val iterator = entries.values.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.users == 0 && entry.isIdle) {
                iterator.remove()
                dropped++
            }
        }
        dropped
    }

    private val Entry.isIdle: Boolean
        get() = lastUsed.elapsedNow() >= idleTimeout

    /**
     * Drops unused entries: the idle ones at the least recently used end of the order, then
     * (least recently used first) until the cache fits. An entry in use is never dropped.
     */
    private fun evict() {
        val iterator = entries.values.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            // Entries later in the access order were used later; an entry in use stays whatever its age.
            if (!entry.isIdle) break
            if (entry.users == 0) iterator.remove()
        }
        if (entries.size <= maxEntries) return
        val byAge = entries.values.iterator()
        while (entries.size > maxEntries && byAge.hasNext()) {
            if (byAge.next().users == 0) byAge.remove()
        }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 1000
        val DEFAULT_IDLE_TIMEOUT: Duration = 30.minutes

        /** How often the application sweeps idle games out, so that memory is freed even without traffic. */
        val SWEEP_INTERVAL: Duration = 1.minutes
    }
}
