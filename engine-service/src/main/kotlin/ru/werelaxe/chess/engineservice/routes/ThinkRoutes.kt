package ru.werelaxe.chess.engineservice.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import ru.werelaxe.chess.engineservice.dto.ThinkRequest
import ru.werelaxe.chess.engineservice.dto.ThinkResponse
import ru.werelaxe.chess.engineservice.service.Searcher
import ru.werelaxe.chess.engineservice.service.ThinkResult

fun Route.thinkRoutes(searcher: Searcher) {
    post("/think") {
        val request = call.receive<ThinkRequest>()
        when (val result = searcher.think(request)) {
            is ThinkResult.Move -> call.respond(ThinkResponse(result.move, result.elapsed.inWholeMilliseconds))
            ThinkResult.GameOver -> call.respond(HttpStatusCode.NoContent)
        }
    }
}
