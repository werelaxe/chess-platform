package ru.werelaxe.chess.engineservice.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.IllegalMoveException
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.engine.ThinkPriority
import ru.werelaxe.chess.engineservice.config.AppConfig
import ru.werelaxe.chess.engineservice.dto.ThinkRequest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.TimeSource

/** The outcome of a think request: a move, or nothing because the game is over. */
sealed interface ThinkResult {
    class Move(val move: GameMove, val elapsed: Duration) : ThinkResult

    data object GameOver : ThinkResult
}

/**
 * Replays the history (through the [cache]) and runs the search on a pool of
 * [parallelism] threads. Requests beyond that wait for a thread, humans first: a thread that
 * frees up goes to the oldest waiting [ThinkPriority.HUMAN] request, and a
 * [ThinkPriority.BOT] request runs only while no human waits. At most the configured queue
 * limit ([AppConfig.queueLimit]) of requests wait, and a bot may join the queue only while
 * fewer than half of that are waiting, so that
 * half of the queue is kept for humans; the next request is refused as overloaded, which the
 * API retries later or, for a human, searches elsewhere. Under load the thinking budget shrinks
 * with the number of waiting requests, whatever their priority, so that the reply latency
 * stays bounded instead of the queue growing. A search spreads over the [helpers] that are
 * idle when it runs, so that a quiet service answers faster.
 */
class Searcher(
    config: AppConfig,
    val helpers: HelperPool = HelperPool(config.helperThreads),
    private val engine: Engine = ChessEngines(helpers.parallelism),
    val cache: ReplayCache = ReplayCache(config.cacheSize),
) {
    private val log = LoggerFactory.getLogger(Searcher::class.java)

    val parallelism: Int = config.parallelism
    private val minBudgetRatio = config.minBudgetRatio

    private val threadCounter = AtomicInteger()
    private val threads = Executors.newFixedThreadPool(parallelism) { runnable ->
        Thread(runnable, "search-${threadCounter.incrementAndGet()}").apply { isDaemon = true }
    }
    private val dispatcher = threads.asCoroutineDispatcher()

    /** Holding a permit means running on one of the [threads]. */
    private val admission = Admission(parallelism, config.queueLimit)
    private val searching = AtomicInteger()

    /** Searches running right now. */
    val inFlight: Int
        get() = searching.get()

    /** Requests waiting for a thread, of either priority. */
    val queued: Int
        get() = admission.waiting

    /** Bot-versus-bot requests waiting for a thread; they run once no human waits. */
    val queuedBots: Int
        get() = admission.waitingBots

    /** Validation errors come out as [ApiException] (400), a full queue as 503. */
    suspend fun think(request: ThinkRequest): ThinkResult {
        val game = replay(request.gameId, request.kind, request.moves)
        if (game.isOver) return ThinkResult.GameOver
        val state = game.state
        return admitted(request.priority) {
            val budget = budget(request.level)
            val started = TimeSource.Monotonic.markNow()
            val move = withContext(dispatcher) { engine.chooseMove(state, request.level, budget) }
                ?: throw IllegalStateException("The engine found no move in an ongoing game")
            ThinkResult.Move(move, started.elapsedNow())
        }
    }

    /** Stops the search and helper threads; a search in progress finishes its budget first. */
    fun close() {
        dispatcher.close()
        helpers.close()
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

    /** Runs [block] holding a search permit, queueing for one within the limit of [priority]. */
    private suspend fun <T> admitted(priority: ThinkPriority, block: suspend () -> T): T {
        if (!admission.acquire(priority)) {
            log.warn("Refusing a {} request: {} searches running and {} waiting", priority, searching.get(), admission.waiting)
            val queue = if (priority == ThinkPriority.HUMAN) "the queue" else "the half of the queue open to bot-versus-bot games"
            throw ApiException.overloaded("All $parallelism search threads are busy and $queue is full")
        }
        searching.incrementAndGet()
        try {
            return block()
        } finally {
            searching.decrementAndGet()
            admission.release()
        }
    }

    /**
     * The level's budget scaled by `min(1, parallelism / waiting)`, so that with twice as many
     * requests waiting as threads every search gets half its time, but never less than
     * [minBudgetRatio] of it.
     */
    private fun budget(level: EngineLevel): Duration {
        val waitingNow = admission.waiting
        val scale = if (waitingNow <= parallelism) 1.0 else maxOf(minBudgetRatio, parallelism.toDouble() / waitingNow)
        return level.timeBudget * scale
    }
}

/**
 * The search permits and two FIFO queues of requests waiting for one. A permit that is
 * released goes to the oldest waiting human, else to the oldest waiting bot, else back to the
 * free count, so a free permit never exists while somebody waits and a bot never runs ahead of
 * a waiting human. Humans may wait while fewer than [queueLimit] requests wait, bots while
 * fewer than half of that. The bookkeeping happens under [lock]; the waiting itself happens
 * outside it, on the request's own [CompletableDeferred].
 */
private class Admission(permits: Int, private val queueLimit: Int) {
    private val lock = ReentrantLock()
    private var free = permits
    private val humans = ArrayDeque<CompletableDeferred<Unit>>()
    private val bots = ArrayDeque<CompletableDeferred<Unit>>()

    val waiting: Int
        get() = lock.withLock { humans.size + bots.size }

    val waitingBots: Int
        get() = lock.withLock { bots.size }

    /**
     * Takes a permit, waiting for one within the queue limit of [priority]; false when the
     * queue is full for it. A waiter that is cancelled leaves the queue, and passes the permit
     * on when it was granted one in the meantime.
     */
    suspend fun acquire(priority: ThinkPriority): Boolean {
        val waiter = lock.withLock {
            if (free > 0) {
                free--
                return true
            }
            val limit = if (priority == ThinkPriority.HUMAN) queueLimit else queueLimit / 2
            if (humans.size + bots.size >= limit) return false
            CompletableDeferred<Unit>().also { (if (priority == ThinkPriority.HUMAN) humans else bots).addLast(it) }
        }
        try {
            waiter.await()
        } catch (e: CancellationException) {
            lock.withLock { if (!humans.remove(waiter) && !bots.remove(waiter)) handOver() }
            throw e
        }
        return true
    }

    /** Gives the permit to the next waiter, humans first, or back to the free count. */
    fun release() {
        lock.withLock { handOver() }
    }

    private fun handOver() {
        val next = humans.removeFirstOrNull() ?: bots.removeFirstOrNull()
        if (next == null) free++ else next.complete(Unit)
    }
}
