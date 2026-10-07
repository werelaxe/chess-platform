package ru.werelaxe.chess.engineservice.service

import io.ktor.http.HttpStatusCode

/** An error reported to the client as `{"error": code, "message": ...}` with [status]. */
class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : RuntimeException(message) {
    companion object {
        fun validation(message: String) = ApiException(HttpStatusCode.BadRequest, "validation", message)

        fun overloaded(message: String) = ApiException(HttpStatusCode.ServiceUnavailable, "overloaded", message)
    }
}
