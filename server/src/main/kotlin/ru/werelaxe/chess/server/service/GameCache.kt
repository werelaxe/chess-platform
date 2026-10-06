package ru.werelaxe.chess.server.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.werelaxe.chess.core.Game
import java.util.concurrent.ConcurrentHashMap

/**
 * Replayed [Game] instances keyed by game id, each guarded by its own mutex so that
 * every mutation of a game (moves, joining, resigning, draw offers) is serialized.
 */
class GameCache {
    class Entry {
        val mutex = Mutex()

        /** Only read or written while holding [mutex]. */
        var game: Game? = null
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    suspend fun <T> locked(id: String, block: suspend (Entry) -> T): T {
        val entry = entries.computeIfAbsent(id) { Entry() }
        return entry.mutex.withLock { block(entry) }
    }
}
