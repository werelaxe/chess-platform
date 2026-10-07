package ru.werelaxe.chess.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets

fun Application.configureWebSockets() {
    install(WebSockets) {
        // The server pings on its own so that half-open sockets hit the pong timeout and are reaped.
        pingPeriodMillis = 30_000
        timeoutMillis = 60_000
        maxFrameSize = 64 * 1024
        masking = false
    }
}
