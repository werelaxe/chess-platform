package ru.werelaxe.chess.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import ru.werelaxe.chess.server.dto.CredentialsRequest
import ru.werelaxe.chess.server.dto.UpdateProfileRequest
import ru.werelaxe.chess.server.dto.UserProfile
import ru.werelaxe.chess.server.plugins.AUTH_RATE_LIMIT
import ru.werelaxe.chess.server.plugins.JWT_AUTH
import ru.werelaxe.chess.server.service.ApiException
import ru.werelaxe.chess.server.service.AuthService
import ru.werelaxe.chess.server.service.UserPrincipal

/** The authenticated caller; only valid inside an `authenticate(JWT_AUTH)` block. */
val ApplicationCall.user: UserPrincipal
    get() = principal<UserPrincipal>() ?: throw ApiException.unauthorized()

fun Route.authRoutes(auth: AuthService) {
    route("/api/auth") {
        rateLimit(AUTH_RATE_LIMIT) {
            post("/register") {
                val request = call.receive<CredentialsRequest>()
                call.respond(HttpStatusCode.Created, auth.register(request.username, request.password))
            }
            post("/login") {
                val request = call.receive<CredentialsRequest>()
                call.respond(auth.login(request.username, request.password))
            }
            post("/guest") {
                call.respond(HttpStatusCode.Created, auth.createGuest())
            }
        }
        authenticate(JWT_AUTH) {
            get("/me") {
                call.respond(UserProfile.of(auth.me(call.user)))
            }
            patch("/me") {
                val request = call.receive<UpdateProfileRequest>()
                call.respond(UserProfile.of(auth.updateLocale(call.user, request.locale)))
            }
        }
    }
}
