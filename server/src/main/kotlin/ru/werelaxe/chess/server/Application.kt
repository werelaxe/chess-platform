package ru.werelaxe.chess.server

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory
import ru.werelaxe.chess.server.config.AppConfig
import ru.werelaxe.chess.server.db.DatabaseFactory
import ru.werelaxe.chess.server.plugins.configureCallLogging
import ru.werelaxe.chess.server.plugins.configureCors
import ru.werelaxe.chess.server.plugins.configureRateLimiting
import ru.werelaxe.chess.server.plugins.configureSecurity
import ru.werelaxe.chess.server.plugins.configureSerialization
import ru.werelaxe.chess.server.plugins.configureStatusPages
import ru.werelaxe.chess.server.plugins.configureWebSockets
import ru.werelaxe.chess.server.repository.Repositories
import ru.werelaxe.chess.server.repository.exposed.ExposedGameRepository
import ru.werelaxe.chess.server.repository.exposed.ExposedUserRepository
import ru.werelaxe.chess.server.routes.authRoutes
import ru.werelaxe.chess.server.routes.fallbackRoutes
import ru.werelaxe.chess.server.routes.gameRoutes
import ru.werelaxe.chess.server.routes.healthRoutes
import ru.werelaxe.chess.server.routes.webSocketRoutes
import ru.werelaxe.chess.server.service.AuthService
import ru.werelaxe.chess.server.service.GameService
import ru.werelaxe.chess.server.service.JwtService
import ru.werelaxe.chess.server.ws.GameHub
import java.time.Clock
import java.time.Duration
import kotlin.random.Random
import kotlin.system.exitProcess

fun main() {
    val log = LoggerFactory.getLogger("ru.werelaxe.chess.server.Main")
    val config = try {
        AppConfig.fromEnvironment()
    } catch (e: AppConfig.ConfigurationException) {
        log.error("Refusing to start: {}", e.message)
        exitProcess(1)
    }
    val database = DatabaseFactory.connect(config)
    val repositories = Repositories(
        users = ExposedUserRepository(database),
        games = ExposedGameRepository(database),
    )
    log.info("Starting the chess server on port {}", config.port)
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(config, repositories)
    }.start(wait = true)
}

/**
 * Wires the whole application. Tests call it directly with in-memory repositories,
 * a seeded [random] and a fixed [clock]; [main] calls it with the PostgreSQL repositories.
 */
fun Application.module(
    config: AppConfig,
    repositories: Repositories,
    random: Random = Random.Default,
    clock: Clock = Clock.systemUTC(),
) {
    val jwt = JwtService(config.jwtSecret, Duration.ofDays(config.jwtTtlDays), clock)
    val authService = AuthService(repositories.users, jwt, random, clock, config.bcryptCost)
    val hub = GameHub()
    val gameService = GameService(repositories.games, repositories.users, hub, random, clock)

    configureSerialization()
    configureStatusPages()
    configureCors(config)
    configureCallLogging()
    configureSecurity(jwt)
    configureRateLimiting()
    configureWebSockets()

    routing {
        healthRoutes()
        authRoutes(authService)
        gameRoutes(gameService)
        webSocketRoutes(gameService, hub)
        fallbackRoutes()
    }
}
