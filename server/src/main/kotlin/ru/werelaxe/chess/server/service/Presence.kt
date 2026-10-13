package ru.werelaxe.chess.server.service

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import ru.werelaxe.chess.server.repository.UserRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Refreshes `users.last_seen_at` on authenticated requests, at most once per [interval] per user. */
class Presence(
    private val users: UserRepository,
    private val clock: Clock,
    private val interval: Duration = DEFAULT_INTERVAL,
) {
    private val log = LoggerFactory.getLogger(Presence::class.java)
    private val lastWritten = ConcurrentHashMap<Long, Instant>()

    suspend fun touch(userId: Long) {
        val now = clock.instant()
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

    companion object {
        val DEFAULT_INTERVAL: Duration = Duration.ofMinutes(5)
    }
}
