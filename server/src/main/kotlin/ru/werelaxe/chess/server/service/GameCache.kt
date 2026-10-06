package ru.werelaxe.chess.server.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.werelaxe.chess.core.Game

/**
 * Replayed [Game] instances keyed by game id, each guarded by its own mutex so that
 * every mutation of a game (moves, joining, resigning, draw offers) is serialized.
 *
 * The cache is bounded: beyond [maxEntries] ids, the least recently used entries that no
 * coroutine is using are dropped and their games are replayed from the moves when needed again.
 */
class GameCache(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {
    class Entry {
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
            val entry = entries.getOrPut(id) { Entry() }
            entry.users++
            evict()
            entry
        }
        try {
            return entry.mutex.withLock { block(entry) }
        } finally {
            synchronized(entries) { entry.users-- }
        }
    }

    /** Drops unused entries, least recently used first, until the cache fits; an entry in use is never dropped. */
    private fun evict() {
        if (entries.size <= maxEntries) return
        val iterator = entries.values.iterator()
        while (entries.size > maxEntries && iterator.hasNext()) {
            if (iterator.next().users == 0) iterator.remove()
        }
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 1000
    }
}
