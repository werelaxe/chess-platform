package ru.werelaxe.chess.server.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.SerializationException
import ru.werelaxe.chess.core.IllegalMoveException
import ru.werelaxe.chess.server.dto.ErrorResponse
import ru.werelaxe.chess.server.service.ApiException

/** Maps exceptions to `{"error", "message"}` responses; unmatched routes are handled by [fallbackRoutes]. */
fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ErrorResponse(cause.code, cause.message))
        }
        exception<IllegalMoveException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("illegal_move", cause.message ?: "Illegal move"))
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
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("validation", describe(cause)))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled error on ${call.request.local.uri}", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal", "Internal server error"))
        }
    }
}

private fun describe(cause: Throwable): String {
    val root = generateSequence(cause) { it.cause }.last()
    return (root.message ?: cause.message ?: "Invalid request").lineSequence().first()
}
