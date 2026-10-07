package ru.werelaxe.chess.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import ru.werelaxe.chess.server.dto.ErrorResponse
import ru.werelaxe.chess.server.dto.HealthResponse

fun Route.healthRoutes() {
    get("/api/health") {
        call.respond(HealthResponse("ok"))
    }
}

/** JSON 404 for anything no other route matched (registered last; the tailcard has the lowest priority). */
fun Route.fallbackRoutes() {
    route("{...}") {
        handle {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Resource not found"))
        }
    }
}
