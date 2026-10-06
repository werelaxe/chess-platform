package ru.werelaxe.chess.server

import kotlinx.coroutines.runBlocking
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.server.service.GameCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
}
