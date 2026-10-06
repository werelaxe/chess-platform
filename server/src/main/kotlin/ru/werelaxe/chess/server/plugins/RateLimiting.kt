package ru.werelaxe.chess.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.request.header
import kotlin.time.Duration.Companion.minutes

/** The credential endpoints share this limiter; exceeding it yields 429 `rate_limited` (see [configureStatusPages]). */
val AUTH_RATE_LIMIT = RateLimitName("auth")

private const val AUTH_REQUESTS_PER_MINUTE = 20

fun Application.configureRateLimiting() {
    install(RateLimit) {
        register(AUTH_RATE_LIMIT) {
            rateLimiter(limit = AUTH_REQUESTS_PER_MINUTE, refillPeriod = 1.minutes)
            // Behind nginx every request arrives from the proxy, which overwrites X-Real-IP with the
            // client address; without the proxy the socket peer is the client itself.
            requestKey { call -> call.request.header("X-Real-IP") ?: call.request.origin.remoteAddress }
        }
    }
}
