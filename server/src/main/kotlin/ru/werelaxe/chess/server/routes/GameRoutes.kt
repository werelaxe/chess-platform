package ru.werelaxe.chess.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.server.dto.CreateGameRequest
import ru.werelaxe.chess.server.dto.DrawRequest
import ru.werelaxe.chess.server.dto.GamesResponse
import ru.werelaxe.chess.server.dto.MoveRequest
import ru.werelaxe.chess.server.model.GameFilter
import ru.werelaxe.chess.server.plugins.JWT_AUTH
import ru.werelaxe.chess.server.service.ApiException
import ru.werelaxe.chess.server.service.GameService

private const val DEFAULT_LIMIT = 50
private const val MAX_LIMIT = 100

val ApplicationCall.gameId: String
    get() = parameters["id"] ?: throw ApiException.validation("Missing game id")

fun Route.gameRoutes(games: GameService) {
    route("/api/games") {
        get {
            val query = call.request.queryParameters
            val filter = query["filter"]?.let { value ->
                GameFilter.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                    ?: throw ApiException.validation("Unknown filter '$value'; expected open, active or finished")
            }
            val kind = query["kind"]?.let { value ->
                GameKind.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                    ?: throw ApiException.validation("Unknown kind '$value'; expected CLASSIC or QUANTUM")
            }
            val limit = (query["limit"]?.toIntOrNull() ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
            val offset = (query["offset"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
            call.respond(GamesResponse(games.listPublic(filter, kind, limit, offset)))
        }
        get("/{id}") {
            call.respond(games.get(call.gameId))
        }
        authenticate(JWT_AUTH) {
            post {
                val request = call.receive<CreateGameRequest>()
                call.respond(HttpStatusCode.Created, games.create(call.user, request))
            }
            get("/mine") {
                call.respond(GamesResponse(games.listMine(call.user)))
            }
            post("/{id}/join") {
                call.respond(games.join(call.user, call.gameId))
            }
            post("/{id}/moves") {
                val request = call.receive<MoveRequest>()
                call.respond(games.move(call.user, call.gameId, request.move))
            }
            post("/{id}/resign") {
                call.respond(games.resign(call.user, call.gameId))
            }
            post("/{id}/draw") {
                val request = call.receive<DrawRequest>()
                call.respond(games.draw(call.user, call.gameId, request.action))
            }
        }
    }
}
