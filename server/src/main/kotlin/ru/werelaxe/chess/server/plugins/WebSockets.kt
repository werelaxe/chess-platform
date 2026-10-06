package ru.werelaxe.chess.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets

fun Application.configureWebSockets() {
    install(WebSockets) {
        timeoutMillis = 60_000
        maxFrameSize = 64 * 1024
        masking = false
    }
}
