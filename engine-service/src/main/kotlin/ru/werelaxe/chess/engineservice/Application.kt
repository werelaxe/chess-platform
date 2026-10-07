package ru.werelaxe.chess.engineservice

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory
import ru.werelaxe.chess.engineservice.config.AppConfig
import ru.werelaxe.chess.engineservice.plugins.configureCallLogging
import ru.werelaxe.chess.engineservice.plugins.configureSerialization
import ru.werelaxe.chess.engineservice.plugins.configureStatusPages
import ru.werelaxe.chess.engineservice.routes.fallbackRoutes
import ru.werelaxe.chess.engineservice.routes.healthRoutes
import ru.werelaxe.chess.engineservice.routes.thinkRoutes
import ru.werelaxe.chess.engineservice.service.Searcher
import kotlin.system.exitProcess

fun main() {
    val log = LoggerFactory.getLogger("ru.werelaxe.chess.engineservice.Main")
    val config = try {
        AppConfig.fromEnvironment()
    } catch (e: AppConfig.ConfigurationException) {
        log.error("Refusing to start: {}", e.message)
        exitProcess(1)
    }
    log.info(
        "Starting the engine service on port {} with {} search threads, a queue of {} and a budget floor of {}",
        config.port, config.parallelism, config.queueLimit, config.minBudgetRatio,
    )
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(Searcher(config))
    }.start(wait = true)
}

/** Wires the application around [searcher]; tests call it with a searcher built on a stub engine. */
fun Application.module(searcher: Searcher) {
    monitor.subscribe(ApplicationStopping) { searcher.close() }

    configureSerialization()
    configureStatusPages()
    configureCallLogging()

    routing {
        healthRoutes(searcher)
        thinkRoutes(searcher)
        fallbackRoutes()
    }
}
