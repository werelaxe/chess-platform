package ru.werelaxe.chess.server.service

import io.ktor.http.HttpStatusCode

/** An error reported to the client as `{"error": code, "message": ...}` with [status]. */
class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : RuntimeException(message) {
    companion object {
        fun validation(message: String) = ApiException(HttpStatusCode.BadRequest, "validation", message)

        fun unauthorized(message: String = "Authentication required") =
            ApiException(HttpStatusCode.Unauthorized, "unauthorized", message)

        fun notFound(what: String) = ApiException(HttpStatusCode.NotFound, "not_found", "$what not found")
    }
}
