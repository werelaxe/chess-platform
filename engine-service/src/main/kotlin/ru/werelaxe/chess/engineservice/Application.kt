package ru.werelaxe.chess.engineservice

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.slf4j.LoggerFactory
import ru.werelaxe.chess.engineservice.config.AppConfig
import ru.werelaxe.chess.engineservice.plugins.configureCallLogging
import ru.werelaxe.chess.engineservice.plugins.configureMetrics
import ru.werelaxe.chess.engineservice.plugins.configureSerialization
import ru.werelaxe.chess.engineservice.plugins.configureStatusPages
import ru.werelaxe.chess.engineservice.routes.fallbackRoutes
import ru.werelaxe.chess.engineservice.routes.healthRoutes
import ru.werelaxe.chess.engineservice.routes.metricsRoutes
import ru.werelaxe.chess.engineservice.routes.thinkRoutes
import ru.werelaxe.chess.engineservice.service.Searcher
import ru.werelaxe.chess.engineservice.service.registerMetrics
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
        "Starting the engine service on port {} with {} search threads, {} helper threads, a queue of {}, a budget floor of {} " +
            "and a universe ceiling of {}",
        config.port, config.parallelism, config.helperThreads, config.queueLimit, config.minBudgetRatio,
        if (config.maxUniverses == 0) "none" else config.maxUniverses,
    )
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(Searcher(config))
    }.start(wait = true)
}

/** Wires the application around [searcher]; tests call it with a searcher built on a stub engine. */
fun Application.module(
    searcher: Searcher,
    registry: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
) {
    monitor.subscribe(ApplicationStopping) {
        searcher.close()
        registry.close()
    }
    // The plugin configures the registry (its filters must precede the first meter), then the gauges go in.
    configureMetrics(registry)
    searcher.registerMetrics(registry)

    configureSerialization()
    configureStatusPages()
    configureCallLogging()

    routing {
        healthRoutes(searcher)
        metricsRoutes(registry)
        thinkRoutes(searcher)
        fallbackRoutes()
    }
}
