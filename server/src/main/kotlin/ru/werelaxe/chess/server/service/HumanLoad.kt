package ru.werelaxe.chess.server.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.werelaxe.chess.server.repository.GameRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * How many games people are playing right now and whether the bots should hold back
 * (docs/ARCHITECTURE.md section 2.10): a game with a human is an ACTIVE game with at least one
 * seat that no bot holds (guests count as people) in which something happened within the last
 * [ACTIVITY_WINDOW], and the load is [high] once their number reaches `bot.human_games_limit`
 * (section 2.11; 0 switches the gate off). The window matters: a game whose players walked
 * away stays ACTIVE until the stale sweep ([StaleGames]) finishes it, and counting such games
 * once kept the gate closed for a day with nobody at the boards. The count is reused for
 * [CACHE_TTL] by [clock], so that neither the bot player (before every bot-versus-bot move)
 * nor the matchmaker (every tick) asks the database every time; [refresh] counts at once.
 */
class HumanLoad(
    private val games: GameRepository,
    private val bots: Bots,
    private val settings: Settings,
    private val clock: Clock,
) {
    private class Count(val games: Int, val at: Instant)

    @Volatile
    private var cached: Count? = null

    /** Serialises the counting, so that a burst of callers after an expiry runs one query. */
    private val mutex = Mutex()

    /** Whether the bots must hold back: the limit is set and [humanGames] has reached it. */
    suspend fun high(): Boolean {
        val limit = settings.humanGamesLimit
        return limit > 0 && humanGames() >= limit
    }

    /** ACTIVE games with a human seat played within [ACTIVITY_WINDOW], as counted within the last [CACHE_TTL]. */
    suspend fun humanGames(): Int {
        val now = clock.instant()
        cached?.takeIf { it.fresh(now) }?.let { return it.games }
        return mutex.withLock {
            // Another caller may have counted while this one waited for the lock.
            cached?.takeIf { it.fresh(now) }?.games ?: count(now)
        }
    }

    /** Counts now, whatever the age of the cached count, and returns the result. */
    suspend fun refresh(): Int = mutex.withLock { count(clock.instant()) }

    /**
     * Counts now without touching the cached count: for the metrics, which observe the gate
     * and must not decide when it asks the database.
     */
    suspend fun countNow(): Int = countAt(clock.instant())

    private suspend fun count(now: Instant): Int = countAt(now).also { cached = Count(it, now) }

    private suspend fun countAt(now: Instant): Int = games.countActiveGamesWithHumans(bots.ids, now - ACTIVITY_WINDOW)

    private fun Count.fresh(now: Instant): Boolean = now.isBefore(at + CACHE_TTL)

    companion object {
        /** How long a count is reused before the database is asked again. */
        val CACHE_TTL: Duration = Duration.ofSeconds(10)

        /** How recently a game must have been touched (a move, a draw offer) to count as being played. */
        val ACTIVITY_WINDOW: Duration = Duration.ofMinutes(15)
    }
}
