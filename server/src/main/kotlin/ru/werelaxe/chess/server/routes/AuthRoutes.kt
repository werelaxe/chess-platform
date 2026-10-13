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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.werelaxe.chess.server.dto.CredentialsRequest
import ru.werelaxe.chess.server.model.ProfileField
import ru.werelaxe.chess.server.plugins.AUTH_RATE_LIMIT
import ru.werelaxe.chess.server.plugins.JWT_AUTH
import ru.werelaxe.chess.server.service.ApiException
import ru.werelaxe.chess.server.service.AuthService
import ru.werelaxe.chess.server.service.UserPrincipal

/** The authenticated caller; only valid inside an `authenticate(JWT_AUTH)` block. */
val ApplicationCall.user: UserPrincipal
    get() = principal<UserPrincipal>() ?: throw ApiException.unauthorized()

/** The JSON names of the profile fields `PATCH /api/auth/me` accepts. */
private val PROFILE_FIELDS = mapOf("locale" to ProfileField.LOCALE, "displayName" to ProfileField.DISPLAY_NAME)

/**
 * The fields present in a profile patch: a missing field is left alone, `null` clears it and a
 * string sets it. Parsed by hand because a serializer cannot tell a missing field from a null.
 */
private fun JsonObject.profileFields(): Map<ProfileField, String?> {
    val fields = LinkedHashMap<ProfileField, String?>()
    for ((name, field) in PROFILE_FIELDS) {
        val element = this[name] ?: continue
        fields[field] = when {
            element is JsonNull -> null
            element is JsonPrimitive && element.isString -> element.content
            else -> throw ApiException.validation("$name must be a string or null")
        }
    }
    return fields
}

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
                call.respond(auth.profile(auth.me(call.user)))
            }
            patch("/me") {
                val fields = call.receive<JsonObject>().profileFields()
                call.respond(auth.profile(auth.updateProfile(call.user, fields)))
            }
        }
    }
}
