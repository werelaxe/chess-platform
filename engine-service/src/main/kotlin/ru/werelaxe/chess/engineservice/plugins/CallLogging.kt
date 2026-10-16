package ru.werelaxe.chess.engineservice.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.path
import org.slf4j.event.Level

/** Logs every call except the health checks and the metrics scrapes, which arrive every few seconds. */
fun Application.configureCallLogging() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path() !in UNLOGGED_PATHS }
    }
}

private val UNLOGGED_PATHS = setOf("/health", "/metrics")
