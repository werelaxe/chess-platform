package ru.werelaxe.chess.engineservice.service

import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import java.util.concurrent.atomic.AtomicLong

/**
 * Replayed games keyed by `(gameId, ply)`, so that the next request for the same game only
 * applies the moves played since. [take] removes the entry it returns: a [Game] is mutable,
 * so exactly one request works on it until [put] stores it again under its new ply, and a
 * concurrent request for the same game simply replays from scratch.
 *
 * Bounded to [maxEntries], least recently used first. [hits] and [misses] count the lookups.
 */
class ReplayCache(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {
    private data class Key(val gameId: String, val ply: Int)

    /** [moves] is the history the game was replayed from, kept for the prefix comparison. */
    private class Entry(val kind: GameKind, val moves: List<GameMove>, val game: Game)

    /** Access-ordered, so iteration starts at the least recently used entry; guarded by itself. */
    private val entries = object : LinkedHashMap<Key, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Entry>): Boolean = size > maxEntries
    }

    private val hitCount = AtomicLong()
    private val missCount = AtomicLong()

    val hits: Long
        get() = hitCount.get()

    val misses: Long
        get() = missCount.get()

    val size: Int
        get() = synchronized(entries) { entries.size }

    /**
     * Removes and returns the game at the longest cached prefix of [moves] (at least one
     * move long), or null when nothing usable is cached. The caller applies the remaining
     * moves and hands the game back with [put].
     */
    fun take(gameId: String, kind: GameKind, moves: List<GameMove>): Game? {
        synchronized(entries) {
            for (ply in moves.size downTo 1) {
                val key = Key(gameId, ply)
                val entry = entries[key] ?: continue
                if (entry.kind != kind || entry.moves != moves.subList(0, ply)) continue
                entries.remove(key)
                hitCount.incrementAndGet()
                return entry.game
            }
        }
        missCount.incrementAndGet()
        return null
    }

    /** Stores [game] under its current ply; the initial position is not worth an entry. */
    fun put(gameId: String, game: Game) {
        if (game.moveCount == 0 || maxEntries == 0) return
        val entry = Entry(game.kind, game.history, game)
        synchronized(entries) {
            entries[Key(gameId, game.moveCount)] = entry
        }
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 512
    }
}
