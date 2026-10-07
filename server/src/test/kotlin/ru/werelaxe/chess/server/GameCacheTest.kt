package ru.werelaxe.chess.server

import kotlinx.coroutines.runBlocking
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.server.service.GameCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

class GameCacheTest {
    @Test
    fun evictsTheLeastRecentlyUsedEntry() = runBlocking {
        val cache = GameCache(maxEntries = 2)
        cache.locked("a") { it.game = Game(GameKind.CLASSIC) }
        cache.locked("b") { it.game = Game(GameKind.CLASSIC) }
        cache.locked("a") { }
        // "b" is the least recently used entry, so adding "c" drops it.
        cache.locked("c") { }
        assertEquals(2, cache.size)
        cache.locked("a") { assertNotNull(it.game) }
        cache.locked("b") { assertNull(it.game) }
    }

    @Test
    fun neverEvictsAnEntryInUse() = runBlocking {
        val cache = GameCache(maxEntries = 1)
        cache.locked("a") { entry ->
            entry.game = Game(GameKind.CLASSIC)
            cache.locked("b") { }
            // "a" is in use, so the cache temporarily exceeds its size instead of dropping it.
            assertEquals(2, cache.size)
        }
        cache.locked("a") { assertNotNull(it.game) }
        assertEquals(1, cache.size)
    }

    @Test
    fun dropsEntriesIdleForTheTimeoutWhenAccessed() = runBlocking {
        val time = TestTimeSource()
        val cache = GameCache(maxEntries = 10, idleTimeout = 30.minutes, timeSource = time)
        cache.locked("a") { it.game = Game(GameKind.CLASSIC) }
        time += 20.minutes
        cache.locked("b") { it.game = Game(GameKind.CLASSIC) }
        time += 20.minutes
        // "a" has been idle for 40 minutes and "b" for 20, so any access drops "a" only.
        cache.locked("c") { }
        assertEquals(2, cache.size)
        cache.locked("b") { assertNotNull(it.game) }
        cache.locked("a") { assertNull(it.game) }
        assertEquals(3, cache.size)
    }

    @Test
    fun sweepDropsIdleEntriesButNeverOneInUse() = runBlocking {
        val time = TestTimeSource()
        val cache = GameCache(maxEntries = 10, idleTimeout = 30.minutes, timeSource = time)
        cache.locked("a") { it.game = Game(GameKind.CLASSIC) }
        cache.locked("b") { it.game = Game(GameKind.CLASSIC) }
        time += 29.minutes
        assertEquals(0, cache.sweep())
        cache.locked("b") { entry ->
            time += 31.minutes
            // Both are past the timeout, but "b" is in use.
            assertEquals(1, cache.sweep())
            assertNotNull(entry.game)
        }
        assertEquals(1, cache.size)
        cache.locked("b") { assertNotNull(it.game) }
        cache.locked("a") { assertNull(it.game) }
    }

    @Test
    fun useKeepsAnEntryFresh() = runBlocking {
        val time = TestTimeSource()
        val cache = GameCache(maxEntries = 10, idleTimeout = 30.minutes, timeSource = time)
        cache.locked("a") { it.game = Game(GameKind.CLASSIC) }
        repeat(5) {
            time += 20.minutes
            cache.locked("a") { assertNotNull(it.game) }
        }
        time += 29.minutes
        assertEquals(0, cache.sweep())
        time += 1.minutes
        assertEquals(1, cache.sweep())
        assertEquals(0, cache.size)
    }
}
