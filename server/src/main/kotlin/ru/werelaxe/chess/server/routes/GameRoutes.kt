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
private const val MAX_LIMIT = 200

/** The shape of the ids [ru.werelaxe.chess.server.service.GameIdGenerator] produces. */
private val GAME_ID_PATTERN = Regex("[A-Za-z0-9]{12}")

/** The `{id}` segment; anything that cannot be a game id is not found, without touching the cache. */
val ApplicationCall.gameId: String
    get() = parameters["id"]?.takeIf { GAME_ID_PATTERN.matches(it) } ?: throw ApiException.notFound("Game")

private data class Paging(val limit: Int, val offset: Int)

/** `limit` (default [DEFAULT_LIMIT], clamped to 1..[MAX_LIMIT]) and `offset` (at least 0) of a listing. */
private fun ApplicationCall.paging(): Paging {
    val query = request.queryParameters
    val limit = (query["limit"]?.toIntOrNull() ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
    val offset = (query["offset"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
    return Paging(limit, offset)
}

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
            val (limit, offset) = call.paging()
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
                val (limit, offset) = call.paging()
                call.respond(GamesResponse(games.listMine(call.user, limit, offset)))
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
