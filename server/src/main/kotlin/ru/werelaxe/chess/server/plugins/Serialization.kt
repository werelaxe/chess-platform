package ru.werelaxe.chess.server.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import ru.werelaxe.chess.core.ChessJson

/** Uses the core library's JSON configuration so moves look identical everywhere. */
fun Application.configureSerialization() {
    install(DefaultHeaders)
    install(ContentNegotiation) {
        json(ChessJson.json)
    }
}
