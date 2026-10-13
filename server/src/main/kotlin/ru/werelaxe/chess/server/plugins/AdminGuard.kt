package ru.werelaxe.chess.server.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.Hook
import io.ktor.server.application.call
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.header
import io.ktor.server.response.respond
import ru.werelaxe.chess.server.dto.ErrorResponse
import java.security.MessageDigest

const val ADMIN_TOKEN_HEADER = "X-Admin-Token"

class AdminGuardConfig {
    lateinit var token: String
}

/**
 * Guards the admin API (docs/ARCHITECTURE.md section 2.11): every request under the route
 * must carry `X-Admin-Token` with the configured token, otherwise it is answered with 401
 * `unauthorized` before any handler runs. The tokens are compared through their SHA-256
 * digests in constant time, so that neither the length nor a matching prefix leaks through
 * timing. The token itself is never logged.
 */
val AdminGuard = createRouteScopedPlugin("AdminGuard", ::AdminGuardConfig) {
    val expected = digest(pluginConfig.token)
    on(AdminTokenCheck) { call ->
        val provided = call.request.header(ADMIN_TOKEN_HEADER)
        if (provided == null || !MessageDigest.isEqual(expected, digest(provided))) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse("unauthorized", "A valid $ADMIN_TOKEN_HEADER header is required"),
            )
        }
    }
}

/** Runs the check before the route's handler and ends the call once the check has responded. */
private object AdminTokenCheck : Hook<suspend (ApplicationCall) -> Unit> {
    override fun install(pipeline: ApplicationCallPipeline, handler: suspend (ApplicationCall) -> Unit) {
        pipeline.intercept(ApplicationCallPipeline.Plugins) {
            handler(call)
            if (call.response.isCommitted) finish()
        }
    }
}

private fun digest(value: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
