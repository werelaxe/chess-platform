package ru.werelaxe.chess.server.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import ru.werelaxe.chess.server.dto.BotsResponse
import ru.werelaxe.chess.server.dto.LeaderboardResponse
import ru.werelaxe.chess.server.dto.PlayersResponse
import ru.werelaxe.chess.server.service.ApiException
import ru.werelaxe.chess.server.service.PlayerService

fun Route.playerRoutes(players: PlayerService) {
    route("/api/players") {
        get {
            val (limit, offset) = call.paging()
            call.respond(PlayersResponse(players.search(call.request.queryParameters["query"].orEmpty(), limit, offset)))
        }
        get("/leaderboard") {
            val kind = call.kindParameter() ?: throw ApiException.validation("The kind is required: CLASSIC or QUANTUM")
            val (limit, offset) = call.paging()
            call.respond(LeaderboardResponse(players.leaderboard(kind, limit, offset)))
        }
        get("/{username}") {
            val username = call.parameters["username"] ?: throw ApiException.notFound("Player")
            call.respond(players.profile(username))
        }
    }
    get("/api/bots") {
        call.respond(BotsResponse(players.bots()))
    }
}
