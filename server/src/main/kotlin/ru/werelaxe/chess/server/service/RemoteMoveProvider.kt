package ru.werelaxe.chess.server.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import ru.werelaxe.chess.core.ChessJson
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.engine.ThinkPriority
import ru.werelaxe.chess.server.dto.ErrorResponse
import ru.werelaxe.chess.server.dto.ThinkRequest
import ru.werelaxe.chess.server.dto.ThinkResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** The engine service answered in a way that leaves no move to play; see [RemoteMoveProvider]. */
class EngineServiceException(message: String) : RuntimeException(message)

/**
 * The engine service is overloaded and the move is for a bot-versus-bot game: the game waits
 * for its next wake-up ([BotPlayer]) rather than loading this process with a search while the
 * service is at its busiest; see [RemoteMoveProvider] and [FallbackMoveProvider].
 */
class EngineBusyException(message: String) : RuntimeException(message)

/**
 * Asks the engine service (docs/ARCHITECTURE.md section 2.7) for moves over HTTP. At most
 * [concurrency] requests are in flight at a time. An overloaded service (503) is retried with
 * exponential backoff from [initialBackoff] up to [maxBackoff] until the retry window has
 * elapsed since the first attempt: [retryFor] for a human's move, a quarter of it
 * (`retryFor / [BOT_RETRY_DIVISOR]`) for a bot-versus-bot game, which has nobody waiting at
 * the board and resumes at a matchmaking tick anyway. Then [EngineServiceException] is thrown
 * for a human's move and [EngineBusyException] for a bot's; a connection failure, a timeout
 * (the level's budget plus [timeoutMargin]) or any other answer throws at once, whatever the
 * priority. The caller ([FallbackMoveProvider]) decides what to do then.
 *
 * The provider owns [engine] and closes it together with its client.
 */
class RemoteMoveProvider(
    private val engine: HttpClientEngine,
    baseUrl: String,
    private val retryFor: Duration = 30.seconds,
    concurrency: Int = 64,
    private val timeoutMargin: Duration = 15.seconds,
    private val initialBackoff: Duration = 100.milliseconds,
    private val maxBackoff: Duration = 2.seconds,
) : MoveProvider {
    private val log = LoggerFactory.getLogger(RemoteMoveProvider::class.java)
    private val thinkUrl = baseUrl.trimEnd('/') + "/think"
    private val permits = Semaphore(concurrency)
    private val client = HttpClient(engine) {
        install(ContentNegotiation) { json(ChessJson.json) }
        install(HttpTimeout)
    }

    override suspend fun chooseMove(
        gameId: String,
        kind: GameKind,
        level: EngineLevel,
        moves: List<GameMove>,
        priority: ThinkPriority,
    ): GameMove? {
        val request = ThinkRequest(gameId, kind, level, moves, priority)
        val timeout = level.timeBudget + timeoutMargin
        val retryWindow = if (priority == ThinkPriority.BOT) retryFor / BOT_RETRY_DIVISOR else retryFor
        val started = TimeSource.Monotonic.markNow()
        var backoff = initialBackoff
        while (true) {
            val response = permits.withPermit {
                client.post(thinkUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                    timeout { requestTimeoutMillis = timeout.inWholeMilliseconds }
                }
            }
            when (response.status) {
                HttpStatusCode.OK -> return response.body<ThinkResponse>().move
                HttpStatusCode.NoContent -> return null
                HttpStatusCode.ServiceUnavailable -> {
                    val remaining = retryWindow - started.elapsedNow()
                    if (!remaining.isPositive()) {
                        val message = "Engine service overloaded for $retryWindow: ${describe(response)}"
                        throw if (priority == ThinkPriority.BOT) EngineBusyException(message) else EngineServiceException(message)
                    }
                    log.debug("Engine service overloaded for game {}; retrying in {}", gameId, backoff)
                    delay(minOf(backoff, remaining))
                    backoff = minOf(backoff * 2, maxBackoff)
                }
                else -> throw EngineServiceException("Engine service answered ${response.status}: ${describe(response)}")
            }
        }
    }

    override fun close() {
        client.close()
        engine.close()
    }

    /** The service's error message when the body is one of its error responses; otherwise the start of the body. */
    private suspend fun describe(response: HttpResponse): String {
        val text = response.bodyAsText()
        return runCatching { ChessJson.json.decodeFromString(ErrorResponse.serializer(), text).message }
            .getOrNull() ?: text.take(MAX_QUOTED_BODY)
    }

    companion object {
        /** A bot-versus-bot game retries an overloaded service for this fraction of [retryFor] only. */
        const val BOT_RETRY_DIVISOR = 4
        private const val MAX_QUOTED_BODY = 200
    }
}
