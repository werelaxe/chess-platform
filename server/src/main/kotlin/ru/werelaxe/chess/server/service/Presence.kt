package ru.werelaxe.chess.server.service

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import ru.werelaxe.chess.server.repository.UserRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Refreshes `users.last_seen_at` on authenticated requests, at most once per [interval] per
 * user, and remembers in memory when each user was last seen, for [onlineUsers]. Bots never
 * authenticate, so only people (guests included) pass through here.
 */
class Presence(
    private val users: UserRepository,
    private val clock: Clock,
    private val interval: Duration = DEFAULT_INTERVAL,
) {
    private val log = LoggerFactory.getLogger(Presence::class.java)
    private val lastWritten = ConcurrentHashMap<Long, Instant>()
    private val lastSeen = ConcurrentHashMap<Long, Instant>()

    suspend fun touch(userId: Long) {
        val now = clock.instant()
        lastSeen[userId] = now
        var write = false
        lastWritten.compute(userId) { _, last ->
            if (last == null || Duration.between(last, now) >= interval) {
                write = true
                now
            } else {
                last
            }
        }
        if (!write) return
        try {
            users.touchLastSeen(userId, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Presence is best effort; the request itself must not fail because of it.
            log.warn("Could not refresh last_seen_at of user {}: {}", userId, e.toString())
        }
    }

    /**
     * How many distinct users made an authenticated request within the last [window]: the
     * people online of the metrics (docs/ARCHITECTURE.md section 4.1), as seen by this
     * instance. The users seen earlier than that are forgotten, so the memory stays bounded.
     */
    fun onlineUsers(window: Duration = ONLINE_WINDOW): Int {
        val cutoff = clock.instant() - window
        lastSeen.values.removeIf { !it.isAfter(cutoff) }
        return lastSeen.size
    }

    companion object {
        val DEFAULT_INTERVAL: Duration = Duration.ofMinutes(5)

        /** How recently a user must have made a request to count as online. */
        val ONLINE_WINDOW: Duration = Duration.ofMinutes(5)
    }
}
