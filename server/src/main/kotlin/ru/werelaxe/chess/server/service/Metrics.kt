package ru.werelaxe.chess.server.service

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.repository.GameRepository
import ru.werelaxe.chess.server.ws.GameHub
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The application gauges of `GET /metrics` (docs/ARCHITECTURE.md section 4.1). A scrape never
 * touches the database: the gauges read fields that [refresh] fills from the repositories, and
 * [loop] refreshes them every [REFRESH_INTERVAL]. Only the WebSocket count is read live, from
 * the [hub].
 *
 * The players online are counted per kind. A person is online while they made an authenticated
 * request within the last [Presence.ONLINE_WINDOW] (the web client polls its invitations every
 * 30 s while a session exists, so anybody with the site open counts; guests included, bots
 * never, as they do not authenticate). A bot is online while it holds a seat in an ACTIVE game.
 */
class Metrics(
    registry: MeterRegistry,
    private val games: GameRepository,
    private val bots: Bots,
    private val presence: Presence,
    private val humanLoad: HumanLoad,
    private val settings: Settings,
    private val hub: GameHub,
) {
    private val log = LoggerFactory.getLogger(Metrics::class.java)

    private val activeByKind = GameKind.entries.associateWith { AtomicInteger() }
    private val waiting = AtomicInteger()
    private val botVersusBot = AtomicInteger()
    private val withHumans = AtomicInteger()
    private val humanGamesRecent = AtomicInteger()
    private val humanGamesLimit = AtomicInteger()
    private val humansOnline = AtomicInteger()
    private val botsOnline = AtomicInteger()

    init {
        for ((kind, count) in activeByKind) {
            gauge(registry, "chess_games_active", count, "ACTIVE games of the kind", "kind", kind.name)
        }
        gauge(registry, "chess_games_waiting", waiting, "WAITING games: open games and pending invitations")
        gauge(registry, "chess_games_active_bot_versus_bot", botVersusBot, "ACTIVE games with a bot in both seats")
        gauge(
            registry,
            "chess_games_active_with_humans",
            withHumans,
            "ACTIVE games with at least one human seat, whatever their age",
        )
        gauge(
            registry,
            "chess_human_games_recent",
            humanGamesRecent,
            "ACTIVE games with a human seat played within the last 15 minutes, as the human-load gate counts them against bot.human_games_limit",
        )
        gauge(
            registry,
            "chess_human_games_limit",
            humanGamesLimit,
            "bot.human_games_limit: the number of recent human games from which the bots hold back; 0 when there is no limit",
        )
        gauge(
            registry,
            "chess_players_online",
            humansOnline,
            "Distinct people who made an authenticated request within the last five minutes",
            "kind",
            "human",
        )
        gauge(registry, "chess_players_online", botsOnline, "Bots holding a seat in an ACTIVE game", "kind", "bot")
        Gauge.builder("chess_ws_connections", hub) { it.subscriptionCount().toDouble() }
            .description("Open game WebSocket connections; they are anonymous, so a player with two tabs counts twice")
            .register(registry)
    }

    /** Counts everything now. */
    suspend fun refresh() {
        waiting.set(games.countByPhase()[GamePhase.WAITING] ?: 0)
        val byKind = games.countActiveByKind()
        for ((kind, count) in activeByKind) count.set(byKind[kind] ?: 0)
        botVersusBot.set(games.countActiveGamesAmong(bots.ids))
        withHumans.set(games.countActiveGamesWithHumans(bots.ids, Instant.EPOCH))
        humanGamesRecent.set(humanLoad.countNow())
        humanGamesLimit.set(settings.humanGamesLimit)
        val seated = games.listActiveGamesOf(bots.ids).flatMap { listOfNotNull(it.whiteId, it.blackId) }
        botsOnline.set(seated.filter(bots::isBot).distinct().size)
        humansOnline.set(presence.onlineUsers())
    }

    /** Refreshes at once and then every [REFRESH_INTERVAL]; a failed refresh keeps the previous values and is retried. */
    suspend fun loop() {
        while (true) {
            try {
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Could not refresh the metrics: {}", e.toString())
            }
            delay(REFRESH_INTERVAL)
        }
    }

    private fun gauge(registry: MeterRegistry, name: String, value: AtomicInteger, description: String, vararg tags: String) {
        Gauge.builder(name, value) { it.get().toDouble() }.description(description).tags(*tags).register(registry)
    }

    companion object {
        /** How often the gauges are recounted from the repositories. */
        val REFRESH_INTERVAL: Duration = 15.seconds
    }
}
