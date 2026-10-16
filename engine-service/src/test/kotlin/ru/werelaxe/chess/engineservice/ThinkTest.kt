package ru.werelaxe.chess.engineservice

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import ru.werelaxe.chess.core.ChessJson
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.QuantumState
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.engine.ThinkPriority
import ru.werelaxe.chess.engineservice.config.AppConfig
import ru.werelaxe.chess.engine.ChessEngine
import ru.werelaxe.chess.engineservice.dto.ThinkRequest
import ru.werelaxe.chess.engineservice.service.Engine
import ru.werelaxe.chess.engineservice.service.HelperPool
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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

    /** A split leaves two universes, so a ceiling of one puts the position above it. */
    private val fog = listOf(split("g1", "f3", "h3"))

    /**
     * [plies] pawn splits, one pawn after another from the a-file: a pawn nobody touched stands
     * on its square in every universe, so every split doubles them and the fog has 2^[plies].
     */
    private fun pawnFog(plies: Int): List<GameMove> = (0 until plies).map { ply ->
        val file = "abcdefgh"[ply / 2]
        if (ply % 2 == 0) split("${file}2", "${file}3", "${file}4") else split("${file}7", "${file}6", "${file}5")
    }

    @Test
    fun answersAPositionAboveTheUniverseCeilingWithALegalMove() =
        serviceTest(AppConfig(parallelism = 1, queueLimit = 1, maxUniverses = 16)) {
            // 256 universes searched on the 16 most probable: the move must be legal in all 256.
            val move = think("fog", pawnFog(8), kind = GameKind.QUANTUM, level = EngineLevel.HARD)
            if (move is GameMove.Observe) assertNull(move.outcome)
        }

    /** An engine that records the level, the budget and the universes of every search and answers [answer] (1... e5 by default). */
    private class RecordingEngine(answer: GameMove = normal("e7e5")) {
        val searches = CopyOnWriteArrayList<Pair<EngineLevel, Duration>>()
        val universes = CopyOnWriteArrayList<Int>()
        val engine = Engine { state, level, budget ->
            searches += level to budget
            universes += (state as? QuantumState)?.universeCount ?: 0
            answer
        }
    }

    @Test
    fun searchesAPositionAboveTheUniverseCeilingShallowly() {
        val recording = RecordingEngine()
        serviceTest(AppConfig(parallelism = 1, queueLimit = 1, maxUniverses = 4), recording.engine) {
            // Eight universes above a ceiling of four: the engine sees four of them, at NOVICE within 100 ms.
            assertEquals(normal("e7e5"), think("fog", pawnFog(3), kind = GameKind.QUANTUM, level = EngineLevel.HARD))
            assertEquals(listOf(EngineLevel.NOVICE to 100.milliseconds), recording.searches)
            assertEquals(listOf(4), recording.universes)

            // A quantum position within the ceiling, and classic chess, get the level asked for and the whole position.
            think("quantum", fog, kind = GameKind.QUANTUM, level = EngineLevel.HARD)
            think("classic", moves("e2e4"), level = EngineLevel.HARD)
            val full = EngineLevel.HARD to EngineLevel.HARD.timeBudget
            assertEquals(listOf(full, full), recording.searches.drop(1))
            assertEquals(listOf(2, 0), recording.universes.drop(1))
        }
    }

    @Test
    fun aShallowSearchSeesAFewHundredUniversesAtMost() {
        // Ten pawn splits leave white to move with the king's knight and the f-pawn untouched.
        val recording = RecordingEngine(answer = normal("g1f3"))
        serviceTest(AppConfig(parallelism = 1, queueLimit = 1, maxUniverses = 1000), recording.engine) {
            think("fog", pawnFog(10), kind = GameKind.QUANTUM, level = EngineLevel.HARD)
            assertEquals(listOf(EngineLevel.NOVICE to 100.milliseconds), recording.searches)
            assertEquals(listOf(256), recording.universes, "1024 universes, searched on the 256 most probable")
        }
    }

    @Test
    fun aUniverseCeilingOfZeroMeansNoCeiling() {
        val recording = RecordingEngine()
        serviceTest(AppConfig(parallelism = 1, queueLimit = 1, maxUniverses = 0), recording.engine) {
            think("fog", fog, kind = GameKind.QUANTUM, level = EngineLevel.HARD)
            assertEquals(listOf(EngineLevel.HARD to EngineLevel.HARD.timeBudget), recording.searches)
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
        thinkRaw("""{"gameId":"g","kind":"CLASSIC","level":"EASY","moves":[],"priority":"URGENT"}""")
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
    fun searchesWithTheHelpersAndReturnsThem() {
        val helpers = HelperPool(4)
        val engine = Engine { state, level, budget -> ChessEngine(level, Random(1), helpers.parallelism).chooseMove(state, budget) }
        serviceTest(AppConfig(parallelism = 2, queueLimit = 4, helperThreads = 4), engine, helpers) {
            think("classic", moves("e2e4", "e7e5"), level = EngineLevel.MEDIUM)
            think("quantum", listOf(split("g1", "f3", "h3"), normal("e7e5")), kind = GameKind.QUANTUM, level = EngineLevel.MEDIUM)
            val health = health()
            assertEquals(4, health.helpers)
            assertEquals(0, health.helpersBusy)
            assertEquals(0, helpers.busy)
        }
    }

    @Test
    fun lendsHelpersToARunningSearch() {
        val helpers = HelperPool(3)
        val started = CountDownLatch(3)
        val gate = CountDownLatch(1)
        val spreading = Engine { _, _, _ ->
            // Three items: the search thread and two borrowed helpers.
            helpers.parallelism.map(listOf(1, 2, 3)) {
                started.countDown()
                gate.await()
                it
            }
            normal("e2e4")
        }
        serviceTest(AppConfig(parallelism = 1, queueLimit = 1, helperThreads = 3), spreading, helpers) {
            try {
                coroutineScope {
                    val request = async { thinkResponse("game", emptyList()) }
                    withContext(Dispatchers.IO) { assertTrue(started.await(10, TimeUnit.SECONDS)) }
                    val health = health()
                    assertEquals(3, health.helpers)
                    assertEquals(2, health.helpersBusy)
                    gate.countDown()
                    assertEquals(HttpStatusCode.OK, request.await().status)
                }
            } finally {
                gate.countDown()
            }
            awaitHealth { it.inFlight == 0 && it.helpersBusy == 0 }
        }
    }

    @Test
    fun runsWithoutHelpers() = serviceTest(AppConfig(parallelism = 1, queueLimit = 1, helperThreads = 0)) {
        think("classic", moves("e2e4"))
        assertEquals(0, health().helpers)
    }

    @Test
    fun priorityDefaultsToHumanWhenAbsent() {
        val legacy = """{"gameId":"g","kind":"CLASSIC","level":"EASY","moves":[]}"""
        assertEquals(ThinkPriority.HUMAN, ChessJson.json.decodeFromString(ThinkRequest.serializer(), legacy).priority)
        val bot = """{"gameId":"g","kind":"CLASSIC","level":"EASY","moves":[],"priority":"BOT"}"""
        assertEquals(ThinkPriority.BOT, ChessJson.json.decodeFromString(ThinkRequest.serializer(), bot).priority)
    }

    /** An engine that records the level of every search and holds each one until the test releases [gate]. */
    private class HeldEngine {
        val started = CountDownLatch(1)
        val gate = Semaphore(0)
        val levels = CopyOnWriteArrayList<EngineLevel>()
        val engine = Engine { _, level, _ ->
            levels += level
            started.countDown()
            gate.acquire()
            normal("e2e4")
        }
    }

    @Test
    fun servesWaitingHumansBeforeWaitingBots() {
        val held = HeldEngine()
        serviceTest(AppConfig(parallelism = 1, queueLimit = 4), held.engine) {
            try {
                coroutineScope {
                    // A bot's search runs; another bot and then a human queue behind it (bots think at NOVICE, humans at EASY).
                    val firstBot = async { thinkResponse("bot-1", emptyList(), level = EngineLevel.NOVICE, priority = ThinkPriority.BOT) }
                    withContext(Dispatchers.IO) { assertTrue(held.started.await(10, TimeUnit.SECONDS)) }
                    val secondBot = async { thinkResponse("bot-2", emptyList(), level = EngineLevel.NOVICE, priority = ThinkPriority.BOT) }
                    awaitHealth { it.queued == 1 && it.queuedBots == 1 }
                    val human = async { thinkResponse("human", emptyList(), level = EngineLevel.EASY) }
                    awaitHealth { it.queued == 2 && it.queuedBots == 1 }

                    held.gate.release()
                    assertEquals(HttpStatusCode.OK, firstBot.await().status)
                    // The freed thread went to the human although the bot had waited longer.
                    awaitHealth { it.inFlight == 1 && it.queued == 1 && it.queuedBots == 1 }
                    held.gate.release()
                    assertEquals(HttpStatusCode.OK, human.await().status)
                    awaitHealth { it.inFlight == 1 && it.queued == 0 }
                    held.gate.release()
                    assertEquals(HttpStatusCode.OK, secondBot.await().status)
                }
            } finally {
                held.gate.release(10)
            }
            assertEquals(listOf(EngineLevel.NOVICE, EngineLevel.EASY, EngineLevel.NOVICE), held.levels)
            awaitHealth { it.inFlight == 0 && it.queued == 0 }
        }
    }

    @Test
    fun keepsHalfOfTheQueueForHumans() {
        val held = HeldEngine()
        serviceTest(AppConfig(parallelism = 1, queueLimit = 4), held.engine) {
            try {
                coroutineScope {
                    val running = async { thinkResponse("running", emptyList(), priority = ThinkPriority.BOT) }
                    withContext(Dispatchers.IO) { assertTrue(held.started.await(10, TimeUnit.SECONDS)) }
                    val bots = (1..2).map { async { thinkResponse("bot-$it", emptyList(), priority = ThinkPriority.BOT) } }
                    awaitHealth { it.queued == 2 && it.queuedBots == 2 }

                    // Half of the queue (two of four) is taken: the next bot is refused, while a human
                    // still waits, and so does a request without a priority, which is a human's.
                    thinkResponse("bot-3", emptyList(), priority = ThinkPriority.BOT).assertError(HttpStatusCode.ServiceUnavailable, "overloaded")
                    assertEquals(2, health().queued)
                    val legacy = async { thinkRaw("""{"gameId":"legacy","kind":"CLASSIC","level":"EASY","moves":[]}""") }
                    awaitHealth { it.queued == 3 && it.queuedBots == 2 }
                    val human = async { thinkResponse("human-1", emptyList()) }
                    awaitHealth { it.queued == 4 && it.queuedBots == 2 }
                    // The whole queue is taken now: everybody is refused.
                    thinkResponse("human-2", emptyList()).assertError(HttpStatusCode.ServiceUnavailable, "overloaded")
                    thinkResponse("bot-4", emptyList(), priority = ThinkPriority.BOT).assertError(HttpStatusCode.ServiceUnavailable, "overloaded")
                    assertEquals(4, health().queued)

                    held.gate.release(5)
                    for (request in listOf(running, legacy, human) + bots) assertEquals(HttpStatusCode.OK, request.await().status)
                }
            } finally {
                held.gate.release(10)
            }
            awaitHealth { it.inFlight == 0 && it.queued == 0 && it.queuedBots == 0 }
        }
    }

    @Test
    fun reportsItsHealth() = serviceTest(AppConfig(parallelism = 3, queueLimit = 12)) {
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            """{"status":"ok","parallelism":3,"inFlight":0,"queued":0,"queuedBots":0,"helpers":1,"helpersBusy":0}""",
            response.bodyAsText(),
        )
    }

    @Test
    fun answersUnknownPathsWithJson() = serviceTest {
        client.get("/nothing").assertError(HttpStatusCode.NotFound, "not_found")
    }
}
