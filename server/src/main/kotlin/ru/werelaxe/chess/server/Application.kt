package ru.werelaxe.chess.server

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import ru.werelaxe.chess.engine.ChessEngine
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
import ru.werelaxe.chess.server.repository.exposed.ExposedRatingRepository
import ru.werelaxe.chess.server.repository.exposed.ExposedSettingsRepository
import ru.werelaxe.chess.server.repository.exposed.ExposedUserRepository
import ru.werelaxe.chess.server.routes.adminRoutes
import ru.werelaxe.chess.server.routes.authRoutes
import ru.werelaxe.chess.server.routes.fallbackRoutes
import ru.werelaxe.chess.server.routes.gameRoutes
import ru.werelaxe.chess.server.routes.healthRoutes
import ru.werelaxe.chess.server.routes.playerRoutes
import ru.werelaxe.chess.server.routes.webSocketRoutes
import ru.werelaxe.chess.server.service.AdminService
import ru.werelaxe.chess.server.service.AuthService
import ru.werelaxe.chess.server.service.BotMatchmaker
import ru.werelaxe.chess.server.service.BotRoster
import ru.werelaxe.chess.server.service.Bots
import ru.werelaxe.chess.server.service.GameCache
import ru.werelaxe.chess.server.service.GameService
import ru.werelaxe.chess.server.service.HumanLoad
import ru.werelaxe.chess.server.service.JwtService
import ru.werelaxe.chess.server.service.MoveProvider
import ru.werelaxe.chess.server.service.PlayerService
import ru.werelaxe.chess.server.service.Presence
import ru.werelaxe.chess.server.service.Settings
import ru.werelaxe.chess.server.ws.GameHub
import java.time.Clock
import java.time.Duration
import kotlin.random.Random
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.minutes

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
        ratings = ExposedRatingRepository(database),
        settings = ExposedSettingsRepository(database),
    )
    val moveProvider = MoveProvider.fromConfig(config)
    log.info("Starting the chess server on port {}", config.port)
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(config, repositories, moveProvider = moveProvider)
    }.start(wait = true)
}

/** The services behind the routes; tests reach them to drive what no request triggers (the matchmaker). */
class AppServices(
    val games: GameService,
    val players: PlayerService,
    val matchmaker: BotMatchmaker,
    val bots: Bots,
    val settings: Settings,
    val humanLoad: HumanLoad,
)

/**
 * Wires the whole application. Tests call it directly with in-memory repositories, a seeded
 * [random], a fixed [clock] and a fast or fake [moveProvider]; [main] calls it with the
 * PostgreSQL repositories and the provider configured by the environment. The application
 * owns the provider from here on and closes it when it stops.
 */
suspend fun Application.module(
    config: AppConfig,
    repositories: Repositories,
    random: Random = Random.Default,
    clock: Clock = Clock.systemUTC(),
    moveProvider: MoveProvider = MoveProvider.fromConfig(config) { ChessEngine(it, random) },
): AppServices {
    // After the migrations (main connects before starting the server) and before the first
    // request; the in-memory repositories of the tests start empty, so this runs every time.
    val bots = BotRoster.ensure(repositories.users, clock)
    val settings = Settings(repositories.settings, config, clock).also { it.refresh() }

    val jwt = JwtService(config.jwtSecret, Duration.ofDays(config.jwtTtlDays), clock)
    val authService = AuthService(repositories.users, repositories.ratings, jwt, random, clock, config.bcryptCost)
    val presence = Presence(repositories.users, clock)
    val hub = GameHub()
    val cache = GameCache(idleTimeout = config.gameIdleMinutes.minutes)
    val humanLoad = HumanLoad(repositories.games, bots, settings, clock)
    val gameService = GameService(
        repositories.games,
        repositories.users,
        repositories.ratings,
        bots,
        hub,
        random,
        clock,
        moveProvider,
        settings,
        humanLoad,
        cache = cache,
    )
    val playerService = PlayerService(repositories.users, repositories.games, repositories.ratings, bots, gameService)
    val matchmaker = BotMatchmaker(
        gameService,
        repositories.games,
        repositories.users,
        bots,
        settings,
        humanLoad,
        Duration.ofHours(config.openGameTtlHours),
        random,
        clock,
    )
    val adminService = AdminService(
        repositories.users,
        repositories.games,
        repositories.ratings,
        bots,
        settings,
        gameService,
        clock,
    )
    monitor.subscribe(ApplicationStopping) {
        gameService.close()
        moveProvider.close()
    }
    // Games a bot was about to move in when the previous instance stopped resume right away.
    gameService.wakeBots()
    // Frees the memory of abandoned games even when no request touches the cache; cancelled with the application.
    launch(CoroutineName("game-cache-sweep")) {
        while (true) {
            delay(GameCache.SWEEP_INTERVAL)
            cache.sweep()
        }
    }
    // A setting changed through another instance reaches this one within a minute.
    launch(CoroutineName("settings-refresh")) { settings.loop() }
    // The loop always runs: `bot.matchmaking` decides at every tick whether it acts, so that
    // matchmaking is paused and resumed through the settings without a restart.
    launch(CoroutineName("bot-matchmaker")) { matchmaker.loop() }

    configureSerialization()
    configureStatusPages()
    configureCors(config)
    configureCallLogging()
    configureSecurity(jwt) { presence.touch(it.id) }
    configureRateLimiting()
    configureWebSockets()

    routing {
        healthRoutes()
        authRoutes(authService)
        gameRoutes(gameService)
        playerRoutes(playerService)
        adminRoutes(adminService, settings, config.adminToken)
        webSocketRoutes(gameService, hub)
        fallbackRoutes()
    }
    return AppServices(gameService, playerService, matchmaker, bots, settings, humanLoad)
}
