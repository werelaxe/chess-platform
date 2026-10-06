package ru.werelaxe.chess.server.routes

import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.launch
import ru.werelaxe.chess.server.service.ApiException
import ru.werelaxe.chess.server.service.GameService
import ru.werelaxe.chess.server.ws.GameHub
import ru.werelaxe.chess.server.ws.ServerEvent

private const val PING = "ping"

/**
 * `GET /api/games/{id}/ws`: streams game events. The socket is anonymous (players and spectators
 * receive the same data); a single writer coroutine drains the subscriber queue to the socket.
 */
fun Route.webSocketRoutes(games: GameService, hub: GameHub) {
    webSocket("/api/games/{id}/ws") {
        val id = try {
            call.gameId
        } catch (e: ApiException) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, e.message))
            return@webSocket
        }
        // Subscribe before taking the snapshot so that nothing published in between is lost; every
        // `game` event is authoritative, so an older event queued ahead of the snapshot is harmless.
        val subscription = hub.subscribe(id)
        if (subscription == null) {
            close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Too many subscribers"))
            return@webSocket
        }
        try {
            val snapshot = try {
                games.get(id)
            } catch (e: ApiException) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, e.message))
                return@webSocket
            }
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
                writer.cancel()
            }
        } finally {
            hub.unsubscribe(subscription)
        }
    }
}
