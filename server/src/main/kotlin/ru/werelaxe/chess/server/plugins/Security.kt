package ru.werelaxe.chess.server.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond
import ru.werelaxe.chess.server.dto.ErrorResponse
import ru.werelaxe.chess.server.service.JwtService

const val JWT_AUTH = "jwt"

/** Bearer JWT authentication from the `Authorization` header only; tokens are never read from the URL. */
fun Application.configureSecurity(jwt: JwtService) {
    install(Authentication) {
        jwt(JWT_AUTH) {
            realm = "chess"
            verifier(jwt.verifier)
            validate { credential -> jwt.principal(credential.payload) }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Invalid or missing token"))
            }
        }
    }
}
