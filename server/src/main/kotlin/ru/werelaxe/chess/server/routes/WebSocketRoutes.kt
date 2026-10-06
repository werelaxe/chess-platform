package ru.werelaxe.chess.server.routes

import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.launch
import ru.werelaxe.chess.server.plugins.JWT_AUTH
import ru.werelaxe.chess.server.service.ApiException
import ru.werelaxe.chess.server.service.GameService
import ru.werelaxe.chess.server.ws.GameHub
import ru.werelaxe.chess.server.ws.ServerEvent

private const val PING = "ping"

/**
 * `GET /api/games/{id}/ws?token=<jwt>`: streams game events. The token is optional so that
 * spectators can watch; a single writer coroutine drains the subscriber queue to the socket.
 */
fun Route.webSocketRoutes(games: GameService, hub: GameHub) {
    authenticate(JWT_AUTH, optional = true) {
        webSocket("/api/games/{id}/ws") {
            val id = call.gameId
            val snapshot = try {
                games.get(id)
            } catch (e: ApiException) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, e.message))
                return@webSocket
            }
            val subscription = hub.subscribe(id)
            hub.send(subscription, ServerEvent.GameUpdated(snapshot))
            val writer = launch {
                for (text in subscription.outbound) send(Frame.Text(text))
            }
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    if (hub.decode(frame.readText())?.type == PING) hub.send(subscription, ServerEvent.Pong)
                }
            } finally {
                hub.unsubscribe(subscription)
                writer.cancel()
            }
        }
    }
}
