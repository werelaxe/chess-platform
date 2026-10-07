package ru.werelaxe.chess.engineservice.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import ru.werelaxe.chess.engineservice.dto.ErrorResponse
import ru.werelaxe.chess.engineservice.dto.HealthResponse
import ru.werelaxe.chess.engineservice.service.Searcher

fun Route.healthRoutes(searcher: Searcher) {
    get("/health") {
        call.respond(HealthResponse("ok", searcher.parallelism, searcher.inFlight, searcher.queued))
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
