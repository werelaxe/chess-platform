package ru.werelaxe.chess.server.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import kotlinx.serialization.SerializationException
import ru.werelaxe.chess.core.IllegalMoveException
import ru.werelaxe.chess.server.dto.ErrorResponse
import ru.werelaxe.chess.server.service.ApiException

/** Maps exceptions to `{"error", "message"}` responses; unmatched routes are handled by [fallbackRoutes]. */
fun Application.configureStatusPages() {
    install(StatusPages) {
        // Produced by the RateLimit plugin, which responds with a bare status.
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respond(status, ErrorResponse("rate_limited", "Too many requests; try again later"))
        }
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ErrorResponse(cause.code, cause.message))
        }
        exception<IllegalMoveException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("illegal_move", cause.message ?: "Illegal move"))
        }
        // A body that is not JSON (missing or non-JSON Content-Type).
        exception<ContentTransformationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("validation", "Expected a JSON body"))
        }
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("validation", describe(cause)))
        }
        exception<JsonConvertException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("validation", describe(cause)))
        }
        exception<SerializationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("validation", describe(cause)))
        }
        exception<IllegalArgumentException> { call, cause ->
            // Library messages stay in the log; clients only get a fixed text.
            call.application.log.warn("Rejected request on {}: {}", call.request.path(), cause.toString())
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("validation", "Invalid request"))
        }
        exception<Throwable> { call, cause ->
            // The path only: query strings never belong in the log.
            call.application.log.error("Unhandled error on ${call.request.path()}", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal", "Internal server error"))
        }
    }
}

/** The first line of the root cause: the JSON parser's description of what is wrong with the body. */
private fun describe(cause: Throwable): String {
    val root = generateSequence(cause) { it.cause }.last()
    return (root.message ?: cause.message ?: "Invalid request").lineSequence().first()
}
