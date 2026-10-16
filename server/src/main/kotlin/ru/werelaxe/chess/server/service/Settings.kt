package ru.werelaxe.chess.server.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import ru.werelaxe.chess.server.config.AppConfig
import ru.werelaxe.chess.server.dto.SettingEntry
import ru.werelaxe.chess.server.dto.SettingSource
import ru.werelaxe.chess.server.repository.SettingsRepository
import java.time.Clock
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * A key of the `settings` table (docs/ARCHITECTURE.md section 2.11): the text shown in
 * listings, where its default comes from and how a value is validated. Values travel as
 * strings; [parse] rejects one that does not fit with a 400 `validation`.
 */
class SettingKey<T : Any> private constructor(
    val name: String,
    val description: String,
    private val defaultOf: (AppConfig) -> T,
    private val parser: (String) -> T?,
    private val expected: String,
) {
    fun default(config: AppConfig): T = defaultOf(config)

    fun defaultString(config: AppConfig): String = default(config).toString()

    fun parse(value: String): T =
        parser(value.trim()) ?: throw ApiException.validation("Setting $name must be $expected")

    /** The stored form of a valid [value]: trimmed and normalised (`TRUE` becomes `true`). */
    fun canonical(value: String): String = parse(value).toString()

    companion object {
        fun boolean(name: String, description: String, defaultOf: (AppConfig) -> Boolean) =
            SettingKey(name, description, defaultOf, { it.lowercase().toBooleanStrictOrNull() }, "true or false")

        fun int(name: String, description: String, min: Int, defaultOf: (AppConfig) -> Int) =
            SettingKey(name, description, defaultOf, { it.toIntOrNull()?.takeIf { v -> v >= min } }, "an integer of at least $min")

        fun long(name: String, description: String, min: Long, defaultOf: (AppConfig) -> Long) =
            SettingKey(name, description, defaultOf, { it.toLongOrNull()?.takeIf { v -> v >= min } }, "an integer of at least $min")
    }
}

/** The known keys, in the order of the table in docs/ARCHITECTURE.md section 2.11. */
object SettingKeys {
    val MATCHMAKING = SettingKey.boolean(
        "bot.matchmaking",
        "Whether the matchmaker loop acts on its ticks; false pauses it",
    ) { it.botMatchmaking }
    val OPEN_GAMES_TARGET = SettingKey.int(
        "bot.open_games_target",
        "Public open games the bots keep waiting in the lobby; 0 switches this off",
        min = 0,
    ) { it.botOpenGamesTarget }
    val LIVE_GAMES_TARGET = SettingKey.int(
        "bot.live_games_target",
        "Bot-versus-bot games the matchmaker keeps in progress; 0 switches this off",
        min = 0,
    ) { it.botLiveGamesTarget }
    val HUMAN_GAMES_LIMIT = SettingKey.int(
        "bot.human_games_limit",
        "Games with a human played within the last 15 minutes at or above which bots start no games with each other and bot-versus-bot games wait; 0 switches this off",
        min = 0,
    ) { it.botHumanGamesLimit }
    val REPLY_PAUSE_MILLIS = SettingKey.long(
        "bot.reply_pause_millis",
        "Shortest pause before a bot answers a human's move, in milliseconds",
        min = 0,
    ) { it.botReplyPauseMillis }
    val VS_BOT_PAUSE_MILLIS = SettingKey.long(
        "bot.vs_bot_pause_millis",
        "Shortest pause between the moves of two bots, in milliseconds",
        min = 0,
    ) { it.botVsBotPauseMillis }
    val MAX_ACTIVE_GAMES = SettingKey.int(
        "bot.max_active_games",
        "Games in progress per bot above which it sits matchmaking out",
        min = 1,
    ) { it.botMaxActiveGames }
    val JOIN_AFTER_MINUTES = SettingKey.long(
        "bot.join_after_minutes",
        "How long an open game waits before a bot may join it, in minutes",
        min = 0,
    ) { it.botJoinAfterMinutes }
    val INVITATION_TTL_MINUTES = SettingKey.long(
        "bot.invitation_ttl_minutes",
        "Lifetime of a bot's invitation to a human, in minutes",
        min = 1,
    ) { it.botInvitationTtlMinutes }
    val TICK_MIN_MINUTES = SettingKey.long(
        "bot.tick_min_minutes",
        "Shortest pause between matchmaker ticks, in minutes",
        min = 1,
    ) { it.botTickMinMinutes }
    val TICK_MAX_MINUTES = SettingKey.long(
        "bot.tick_max_minutes",
        "Longest pause between matchmaker ticks, in minutes; raised to the shortest when lower",
        min = 1,
    ) { it.botTickMaxMinutes }
    val STALE_HOURS = SettingKey.int(
        "game.stale_hours",
        "Hours without a move after which an active game is finished by abandonment with no rating change; 0 switches this off",
        min = 0,
    ) { it.gameStaleHours }

    val ALL: List<SettingKey<*>> = listOf(
        MATCHMAKING,
        OPEN_GAMES_TARGET,
        LIVE_GAMES_TARGET,
        HUMAN_GAMES_LIMIT,
        REPLY_PAUSE_MILLIS,
        VS_BOT_PAUSE_MILLIS,
        MAX_ACTIVE_GAMES,
        JOIN_AFTER_MINUTES,
        INVITATION_TTL_MINUTES,
        TICK_MIN_MINUTES,
        TICK_MAX_MINUTES,
        STALE_HOURS,
    )

    val byName: Map<String, SettingKey<*>> = ALL.associateBy { it.name }
}

/**
 * The runtime settings (docs/ARCHITECTURE.md section 2.11): the `settings` table cached in
 * memory, with [config] providing the default of every key without a row. The cache is
 * refreshed every [REFRESH_INTERVAL] by [loop] and right after every write, so a change made
 * on another instance is picked up within a minute. The typed accessors never block:
 * [BotPlayer] reads the pauses before every move, [BotMatchmaker] its tunables at the start
 * of every tick and [StaleGames] the age of a stale game before every sweep.
 */
class Settings(
    private val repository: SettingsRepository,
    private val config: AppConfig,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(Settings::class.java)

    /** The valid rows of the table as of the last refresh, by key. */
    @Volatile
    private var stored: Map<String, String> = emptyMap()

    /**
     * Reloads the table. A row that does not parse (edited by hand) or whose key is unknown is
     * left out, so that its default applies, and logged once per refresh.
     */
    suspend fun refresh() {
        val valid = LinkedHashMap<String, String>()
        for (row in repository.list()) {
            val key = SettingKeys.byName[row.key]
            if (key == null) {
                log.warn("Ignoring the unknown setting {} in the database", row.key)
                continue
            }
            try {
                key.parse(row.value)
                valid[row.key] = row.value
            } catch (e: ApiException) {
                log.warn("Ignoring the stored value '{}' of setting {}: {}", row.value, row.key, e.message)
            }
        }
        stored = valid
    }

    /** Keeps the cache fresh forever; cancelled with the application. */
    suspend fun loop() {
        while (true) {
            delay(REFRESH_INTERVAL)
            try {
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Could not refresh the settings; the cached values stay in force", e)
            }
        }
    }

    /** The effective value of [key]: the stored one, or the default. */
    operator fun <T : Any> get(key: SettingKey<T>): T = stored[key.name]?.let(key::parse) ?: key.default(config)

    val matchmaking: Boolean
        get() = this[SettingKeys.MATCHMAKING]

    val openGamesTarget: Int
        get() = this[SettingKeys.OPEN_GAMES_TARGET]

    val liveGamesTarget: Int
        get() = this[SettingKeys.LIVE_GAMES_TARGET]

    /** Games with a human at or above which the bots hold back ([HumanLoad]); 0 means never. */
    val humanGamesLimit: Int
        get() = this[SettingKeys.HUMAN_GAMES_LIMIT]

    val replyPause: kotlin.time.Duration
        get() = this[SettingKeys.REPLY_PAUSE_MILLIS].milliseconds

    val vsBotPause: kotlin.time.Duration
        get() = this[SettingKeys.VS_BOT_PAUSE_MILLIS].milliseconds

    val maxActiveGames: Int
        get() = this[SettingKeys.MAX_ACTIVE_GAMES]

    val joinAfter: Duration
        get() = Duration.ofMinutes(this[SettingKeys.JOIN_AFTER_MINUTES])

    val invitationTtl: Duration
        get() = Duration.ofMinutes(this[SettingKeys.INVITATION_TTL_MINUTES])

    val tickMin: Duration
        get() = Duration.ofMinutes(this[SettingKeys.TICK_MIN_MINUTES])

    /** Never below [tickMin], so that the pause between ticks stays well defined. */
    val tickMax: Duration
        get() = maxOf(tickMin, Duration.ofMinutes(this[SettingKeys.TICK_MAX_MINUTES]))

    /** Idle time after which [StaleGames] finishes an ACTIVE game; zero switches the sweep off. */
    val staleGameAge: Duration
        get() = Duration.ofHours(this[SettingKeys.STALE_HOURS].toLong())

    /** Every known key with its effective value, default, source and description, in the documented order. */
    fun list(): List<SettingEntry> = SettingKeys.ALL.map { entry(it) }

    /** The effective values by key, for the overview. */
    fun effective(): Map<String, String> = SettingKeys.ALL.associate { it.name to entry(it).value }

    /** Validates and stores [value] under [name]; unknown keys and values that do not fit are 400 `validation`. */
    suspend fun set(name: String, value: String): SettingEntry {
        val key = known(name)
        val canonical = key.canonical(value)
        repository.upsert(key.name, canonical, clock.instant())
        refresh()
        log.info("Admin: setting {} set to {}", key.name, canonical)
        return entry(key)
    }

    /** Removes the stored value of [name], so that its default applies again. */
    suspend fun reset(name: String): SettingEntry {
        val key = known(name)
        repository.delete(key.name)
        refresh()
        log.info("Admin: setting {} reset to its default {}", key.name, key.defaultString(config))
        return entry(key)
    }

    private fun known(name: String): SettingKey<*> =
        SettingKeys.byName[name] ?: throw ApiException.validation("Unknown setting '$name'")

    private fun entry(key: SettingKey<*>): SettingEntry {
        val value = stored[key.name]
        val default = key.defaultString(config)
        return SettingEntry(
            key = key.name,
            value = value ?: default,
            defaultValue = default,
            source = if (value != null) SettingSource.DB else SettingSource.DEFAULT,
            description = key.description,
        )
    }

    companion object {
        /** How long a change made on another instance may take to reach this one. */
        val REFRESH_INTERVAL = 1.minutes
    }
}
