package ru.werelaxe.chess.server.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.response.respond
import ru.werelaxe.chess.server.dto.ErrorResponse
import ru.werelaxe.chess.server.service.JwtService

const val JWT_AUTH = "jwt"

/**
 * Bearer JWT authentication. The token comes from the `Authorization` header or, for
 * WebSocket connections that cannot set headers from a browser, from the `token` query parameter.
 */
fun Application.configureSecurity(jwt: JwtService) {
    install(Authentication) {
        jwt(JWT_AUTH) {
            realm = "chess"
            verifier(jwt.verifier)
            authHeader { call ->
                runCatching { call.request.parseAuthorizationHeader() }.getOrNull()
                    ?: call.request.queryParameters["token"]?.let { HttpAuthHeader.Single("Bearer", it) }
            }
            validate { credential -> jwt.principal(credential.payload) }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Invalid or missing token"))
            }
        }
    }
}
