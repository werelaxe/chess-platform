package ru.werelaxe.chess.engineservice

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.engineservice.config.AppConfig
import ru.werelaxe.chess.engineservice.service.Engine
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

class ThinkTest {
    @Test
    fun answersAClassicHistoryWithALegalMove() = serviceTest {
        val move = think("classic", moves("e2e4", "e7e5"))
        assertIs<GameMove.Normal>(move)
    }

    @Test
    fun answersAQuantumHistoryWithALegalMove() = serviceTest {
        // A split, then an observation whose outcome the authoritative side decided.
        val history = Game(GameKind.QUANTUM).let { game ->
            val played = ArrayList<GameMove>()
            for (move in listOf(split("g1", "f3", "h3"), normal("e7e5"), observe("f3"))) {
                val resolved = game.resolve(move, Random(2))
                game.apply(resolved)
                played += resolved
            }
            played
        }
        assertIs<GameMove.Observe>(history[2]).let { assertTrue(it.outcome != null) }
        val move = think("quantum", history, kind = GameKind.QUANTUM, level = EngineLevel.MEDIUM)
        if (move is GameMove.Observe) assertNull(move.outcome)
    }

    @Test
    fun returnsObservationsWithoutAnOutcome() {
        val observer = Engine { _, _, _ -> observe("f3") }
        serviceTest(engine = observer) {
            val response = thinkResponse("quantum", listOf(split("g1", "f3", "h3"), normal("e7e5")), kind = GameKind.QUANTUM)
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue("\"type\":\"observe\"" in body && "\"square\":\"f3\"" in body, body)
            assertFalse("outcome" in body, body)
            assertTrue("\"elapsedMillis\":" in body, body)
        }
    }

    @Test
    fun answersAFinishedGameWithNoContent() = serviceTest {
        val response = thinkResponse("mate", FOOLS_MATE)
        assertEquals(HttpStatusCode.NoContent, response.status, response.bodyAsText())
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun rejectsAnIllegalHistory() = serviceTest {
        val response = thinkResponse("illegal", moves("e2e4", "e7e5", "e4e5"))
        response.assertError(HttpStatusCode.BadRequest, "validation")
        assertTrue("ply 2" in response.error().message, response.error().message)
        // Classic chess has no splits.
        thinkResponse("illegal", listOf(split("g1", "f3", "h3"))).assertError(HttpStatusCode.BadRequest, "validation")
    }

    @Test
    fun rejectsUnknownKindsLevelsAndBodies() = serviceTest {
        thinkRaw("""{"gameId":"g","kind":"HEXAGONAL","level":"EASY","moves":[]}""")
            .assertError(HttpStatusCode.BadRequest, "validation")
        thinkRaw("""{"gameId":"g","kind":"CLASSIC","level":"GRANDMASTER","moves":[]}""")
            .assertError(HttpStatusCode.BadRequest, "validation")
        thinkRaw("""{"gameId":"g","kind":"CLASSIC","level":"EASY","moves":[{"type":"teleport"}]}""")
            .assertError(HttpStatusCode.BadRequest, "validation")
        thinkRaw("""{"gameId":"g","kind":"CLASSIC"}""").assertError(HttpStatusCode.BadRequest, "validation")
        thinkRaw("not json").assertError(HttpStatusCode.BadRequest, "validation")
    }

    @Test
    fun reusesTheReplayedGameOfThePreviousRequest() = serviceTest {
        val cache = searcher.cache
        think("game", moves("e2e4"))
        assertEquals(0, cache.hits)
        assertEquals(1, cache.misses)

        // One more move: the cached game is advanced instead of replayed.
        think("game", moves("e2e4", "e7e5"))
        assertEquals(1, cache.hits)
        assertEquals(1, cache.misses)
        assertEquals(1, cache.size)

        // The same request again (a retry) hits the entry at the same ply.
        think("game", moves("e2e4", "e7e5"))
        assertEquals(2, cache.hits)

        // A different history under the same id is not confused with the cached one.
        think("game", moves("d2d4", "d7d5", "g1f3"))
        assertEquals(2, cache.hits)
        assertEquals(2, cache.misses)

        // Another game is a miss; so is a different kind at a cached ply of the same id.
        think("other", moves("e2e4"))
        think("game", listOf(split("g1", "f3", "h3"), normal("e7e5")), kind = GameKind.QUANTUM)
        assertEquals(2, cache.hits)
        assertEquals(4, cache.misses)
    }

    @Test
    fun refusesRequestsBeyondTheQueueLimit() {
        val started = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val blocking = Engine { _, _, _ ->
            started.countDown()
            gate.await()
            normal("e2e4")
        }
        serviceTest(AppConfig(parallelism = 1, queueLimit = 1), blocking) {
            try {
                coroutineScope {
                    val first = async { thinkResponse("first", emptyList()) }
                    withContext(Dispatchers.IO) { assertTrue(started.await(10, TimeUnit.SECONDS)) }
                    val second = async { thinkResponse("second", emptyList()) }
                    awaitHealth { it.queued == 1 }
                    assertEquals(1, health().inFlight)

                    thinkResponse("third", emptyList()).assertError(HttpStatusCode.ServiceUnavailable, "overloaded")
                    // The refused request did not take a place in the queue.
                    assertEquals(1, health().queued)

                    gate.countDown()
                    assertEquals(HttpStatusCode.OK, first.await().status)
                    assertEquals(HttpStatusCode.OK, second.await().status)
                }
            } finally {
                gate.countDown()
            }
            awaitHealth { it.inFlight == 0 && it.queued == 0 }
        }
    }

    @Test
    fun shrinksTheBudgetWhileRequestsAreWaiting() {
        val budgets = CopyOnWriteArrayList<Duration>()
        val started = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val recording = Engine { _, _, budget ->
            budgets += budget
            started.countDown()
            gate.await()
            normal("e2e4")
        }
        serviceTest(AppConfig(parallelism = 1, queueLimit = 6, minBudgetRatio = 0.25), recording) {
            try {
                coroutineScope {
                    val first = async { thinkResponse("first", emptyList()) }
                    withContext(Dispatchers.IO) { assertTrue(started.await(10, TimeUnit.SECONDS)) }
                    val rest = (1..6).map { async { thinkResponse("waiting-$it", emptyList()) } }
                    awaitHealth { it.queued == 6 }
                    gate.countDown()
                    for (request in listOf(first) + rest) assertEquals(HttpStatusCode.OK, request.await().status)
                }
            } finally {
                gate.countDown()
            }
        }
        // The first search saw nothing waiting; the next ones saw 5, 4, 3, 2, 1 and 0 requests
        // behind them: 700 ms x max(0.25, 1 / waiting).
        assertEquals(listOf<Long>(700, 175, 175, 233, 350, 700, 700), budgets.map { it.inWholeMilliseconds })
    }

    @Test
    fun reportsItsHealth() = serviceTest(AppConfig(parallelism = 3, queueLimit = 12)) {
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"ok","parallelism":3,"inFlight":0,"queued":0}""", response.bodyAsText())
    }

    @Test
    fun answersUnknownPathsWithJson() = serviceTest {
        client.get("/nothing").assertError(HttpStatusCode.NotFound, "not_found")
    }
}
