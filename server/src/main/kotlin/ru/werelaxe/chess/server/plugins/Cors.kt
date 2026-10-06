package ru.werelaxe.chess.server.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import ru.werelaxe.chess.server.config.AppConfig

/** Development-only CORS; in production nginx serves the client and the API from one origin. */
fun Application.configureCors(config: AppConfig) {
    if (config.corsOrigins.isEmpty()) return
    install(CORS) {
        for (origin in config.corsOrigins) {
            val url = Url(origin)
            val host = if (url.specifiedPort == 0 || url.specifiedPort == url.protocol.defaultPort) {
                url.host
            } else {
                "${url.host}:${url.specifiedPort}"
            }
            allowHost(host, schemes = listOf(url.protocol.name))
        }
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowNonSimpleContentTypes = true
    }
}
