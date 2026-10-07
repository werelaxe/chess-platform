package ru.werelaxe.chess.engineservice.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import ru.werelaxe.chess.core.ChessJson

/** Uses the core library's JSON configuration so moves look identical everywhere. */
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(ChessJson.json)
    }
}
