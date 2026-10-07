package ru.werelaxe.chess.server

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import ru.werelaxe.chess.core.ChessJson
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.server.dto.ThinkRequest
import ru.werelaxe.chess.server.service.EngineServiceException
import ru.werelaxe.chess.server.service.RemoteMoveProvider
import java.io.IOException
import java.net.ConnectException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class RemoteMoveProviderTest {
    private val json = ContentType.Application.Json.toString()

    private fun MockRequestHandleScope.move(uci: String): HttpResponseData = respond(
        content = """{"move":${ChessJson.encodeMove(normal(uci))},"elapsedMillis":12}""",
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, json),
    )

    private fun MockRequestHandleScope.overloaded(): HttpResponseData = respond(
        content = """{"error":"overloaded","message":"The queue is full"}""",
        status = HttpStatusCode.ServiceUnavailable,
        headers = headersOf(HttpHeaders.ContentType, json),
    )

    /** A provider over a mock engine, with retries short enough for a test. */
    private fun provider(
        retryFor: Duration = 300.milliseconds,
        concurrency: Int = 64,
        timeoutMargin: Duration = 15.seconds,
        handler: MockRequestHandler,
    ) = RemoteMoveProvider(
        MockEngine(handler),
        "http://engine:8081/",
        retryFor = retryFor,
        concurrency = concurrency,
        timeoutMargin = timeoutMargin,
        initialBackoff = 10.milliseconds,
        maxBackoff = 40.milliseconds,
    )

    private suspend fun RemoteMoveProvider.think(moves: List<GameMove> = listOf(normal("e2e4"))): GameMove? =
        chooseMove("Game00000001", GameKind.CLASSIC, EngineLevel.EASY, moves)

    @Test
    fun postsTheGameAndReturnsTheMove() = runBlocking {
        var seen: ThinkRequest? = null
        var url = ""
        var method = HttpMethod.Get
        val provider = provider { request ->
            url = request.url.toString()
            method = request.method
            seen = ChessJson.json.decodeFromString(ThinkRequest.serializer(), request.body.toByteArray().decodeToString())
            move("e7e5")
        }
        provider.use {
            assertEquals(normal("e7e5"), it.think())
        }
        assertEquals("http://engine:8081/think", url)
        assertEquals(HttpMethod.Post, method)
        assertEquals(ThinkRequest("Game00000001", GameKind.CLASSIC, EngineLevel.EASY, listOf(normal("e2e4"))), seen)
    }

    @Test
    fun noContentMeansTheGameIsOver() = runBlocking {
        provider { respond("", HttpStatusCode.NoContent) }.use {
            assertNull(it.think())
        }
    }

    @Test
    fun retriesAnOverloadedService() = runBlocking {
        val attempts = AtomicInteger()
        provider { if (attempts.incrementAndGet() < 3) overloaded() else move("e7e5") }.use {
            assertEquals(normal("e7e5"), it.think())
        }
        assertEquals(3, attempts.get())
    }

    @Test
    fun givesUpOnAnOverloadedServiceAfterTheRetryWindow() = runBlocking {
        val attempts = AtomicInteger()
        val started = TimeSource.Monotonic.markNow()
        provider(retryFor = 200.milliseconds) { attempts.incrementAndGet(); overloaded() }.use {
            val error = assertFailsWith<EngineServiceException> { it.think() }
            assertTrue("The queue is full" in error.message.orEmpty(), error.message)
        }
        assertTrue(started.elapsedNow() >= 200.milliseconds, "gave up after ${started.elapsedNow()}")
        assertTrue(attempts.get() >= 3, "retried ${attempts.get()} times")
    }

    @Test
    fun aConnectionErrorIsNotRetried() = runBlocking {
        val attempts = AtomicInteger()
        provider { attempts.incrementAndGet(); throw ConnectException("Connection refused") }.use {
            assertFailsWith<IOException> { it.think() }
        }
        assertEquals(1, attempts.get())
    }

    @Test
    fun anyOtherAnswerFails() = runBlocking {
        provider {
            respond(
                """{"error":"validation","message":"Illegal move at ply 3"}""",
                HttpStatusCode.BadRequest,
                headersOf(HttpHeaders.ContentType, json),
            )
        }.use {
            val error = assertFailsWith<EngineServiceException> { it.think() }
            assertTrue("400" in error.message.orEmpty() && "Illegal move at ply 3" in error.message.orEmpty(), error.message)
        }
    }

    @Test
    fun timesOutAfterTheLevelsBudgetPlusTheMargin() = runBlocking {
        val started = TimeSource.Monotonic.markNow()
        provider(timeoutMargin = 100.milliseconds) { delay(30.seconds); move("e7e5") }.use {
            assertFailsWith<HttpRequestTimeoutException> { it.think() }
        }
        val elapsed = started.elapsedNow()
        assertTrue(elapsed >= EngineLevel.EASY.timeBudget && elapsed < 10.seconds, "timed out after $elapsed")
    }

    @Test
    fun keepsAtMostTheConfiguredRequestsInFlight() = runBlocking {
        val inFlight = AtomicInteger()
        val peak = AtomicInteger()
        provider(concurrency = 2) {
            val now = inFlight.incrementAndGet()
            peak.accumulateAndGet(now, ::maxOf)
            delay(50)
            inFlight.decrementAndGet()
            move("e7e5")
        }.use { provider ->
            val moves = (1..6).map { async { provider.think() } }.awaitAll()
            assertTrue(moves.all { it == normal("e7e5") })
        }
        assertEquals(2, peak.get())
    }
}
