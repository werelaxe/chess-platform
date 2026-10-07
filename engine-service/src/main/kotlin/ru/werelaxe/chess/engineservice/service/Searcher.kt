package ru.werelaxe.chess.engineservice.service

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.IllegalMoveException
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.engineservice.config.AppConfig
import ru.werelaxe.chess.engineservice.dto.ThinkRequest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.TimeSource

/** The outcome of a think request: a move, or nothing because the game is over. */
sealed interface ThinkResult {
    class Move(val move: GameMove, val elapsed: Duration) : ThinkResult

    data object GameOver : ThinkResult
}

/**
 * Replays the history (through the [cache]) and runs the search on a pool of
 * [parallelism] threads. Requests beyond that wait for a thread, at most [queueLimit] of
 * them: the next one is refused as overloaded, which the API retries elsewhere or later.
 * Under load the thinking budget shrinks with the number of waiting requests so that the
 * reply latency stays bounded instead of the queue growing.
 */
class Searcher(
    config: AppConfig,
    private val engine: Engine = ChessEngines,
    val cache: ReplayCache = ReplayCache(config.cacheSize),
) {
    private val log = LoggerFactory.getLogger(Searcher::class.java)

    val parallelism: Int = config.parallelism
    private val queueLimit = config.queueLimit
    private val minBudgetRatio = config.minBudgetRatio

    private val threadCounter = AtomicInteger()
    private val threads = Executors.newFixedThreadPool(parallelism) { runnable ->
        Thread(runnable, "search-${threadCounter.incrementAndGet()}").apply { isDaemon = true }
    }
    private val dispatcher = threads.asCoroutineDispatcher()

    /** Holding a permit means running on one of the [threads]. */
    private val permits = Semaphore(parallelism)
    private val waiting = AtomicInteger()
    private val searching = AtomicInteger()

    /** Searches running right now. */
    val inFlight: Int
        get() = searching.get()

    /** Requests waiting for a thread. */
    val queued: Int
        get() = waiting.get()

    /** Validation errors come out as [ApiException] (400), a full queue as 503. */
    suspend fun think(request: ThinkRequest): ThinkResult {
        val game = replay(request.gameId, request.kind, request.moves)
        if (game.isOver) return ThinkResult.GameOver
        val state = game.state
        return admitted {
            val budget = budget(request.level)
            val started = TimeSource.Monotonic.markNow()
            val move = withContext(dispatcher) { engine.chooseMove(state, request.level, budget) }
                ?: throw IllegalStateException("The engine found no move in an ongoing game")
            ThinkResult.Move(move, started.elapsedNow())
        }
    }

    /** Stops the search threads; a search in progress finishes its budget first. */
    fun close() {
        dispatcher.close()
    }

    /**
     * The game after [moves], from the cache when a prefix of the history is known: the
     * remaining moves are applied to the cached game, which is then cached under the new ply.
     */
    private fun replay(gameId: String, kind: GameKind, moves: List<GameMove>): Game {
        val game = cache.take(gameId, kind, moves) ?: Game(kind)
        for (ply in game.moveCount until moves.size) {
            try {
                game.apply(moves[ply])
            } catch (e: IllegalMoveException) {
                throw ApiException.validation("Illegal move at ply $ply: ${e.message}")
            }
        }
        cache.put(gameId, game)
        return game
    }

    /** Runs [block] holding a search permit, queueing for one within the limit. */
    private suspend fun <T> admitted(block: suspend () -> T): T {
        if (!permits.tryAcquire()) {
            if (waiting.incrementAndGet() > queueLimit) {
                waiting.decrementAndGet()
                log.warn("Refusing a request: {} searches running and {} waiting", searching.get(), queueLimit)
                throw ApiException.overloaded("All $parallelism search threads are busy and the queue is full")
            }
            try {
                permits.acquire()
            } finally {
                waiting.decrementAndGet()
            }
        }
        searching.incrementAndGet()
        try {
            return block()
        } finally {
            searching.decrementAndGet()
            permits.release()
        }
    }

    /**
     * The level's budget scaled by `min(1, parallelism / waiting)`, so that with twice as many
     * requests waiting as threads every search gets half its time, but never less than
     * [minBudgetRatio] of it.
     */
    private fun budget(level: EngineLevel): Duration {
        val waitingNow = waiting.get()
        val scale = if (waitingNow <= parallelism) 1.0 else maxOf(minBudgetRatio, parallelism.toDouble() / waitingNow)
        return level.timeBudget * scale
    }
}
