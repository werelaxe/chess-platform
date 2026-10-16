package ru.werelaxe.chess.server.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import ru.werelaxe.chess.engine.EngineLevel

/**
 * `GET /api/admin/overview` (docs/ARCHITECTURE.md section 2.11): [activeBotGames] are the games
 * in progress with a bot seat, [humanGames] those with a human seat played within the last
 * fifteen minutes (the count the human-load gate of section 2.10 judges, taken afresh), and
 * [settings] the effective values by key.
 */
@Serializable
data class AdminOverview(
    val users: Int,
    val guests: Int,
    val bots: Int,
    val games: GameCounts,
    val activeBotGames: Int,
    val humanGames: Int,
    val settings: Map<String, String>,
)

@Serializable
data class GameCounts(val waiting: Int, val active: Int, val finished: Int)

/** A bot as the admin API lists it: a [PlayerSummary] plus its level, legacy flag and game counts. */
@Serializable
data class AdminBot(
    val user: UserRef,
    val ratings: PlayerRatings,
    val level: EngineLevel,
    /** Kept for its old games only: neither offered to players nor used by the matchmaker. */
    val legacy: Boolean,
    /** Games in progress in which the bot holds a seat. */
    val activeGames: Int,
    /** Games of any phase in which the bot holds a seat. */
    val totalGames: Int,
) {
    fun withGames(activeGameList: List<GameSummary>, recentGames: List<GameSummary>) = AdminBotDetails(
        user = user,
        ratings = ratings,
        level = level,
        legacy = legacy,
        activeGames = activeGames,
        totalGames = totalGames,
        activeGameList = activeGameList,
        recentGames = recentGames,
    )
}

/** [AdminBot] plus the games in progress and the newest games of the bot. */
@Serializable
data class AdminBotDetails(
    val user: UserRef,
    val ratings: PlayerRatings,
    val level: EngineLevel,
    val legacy: Boolean,
    val activeGames: Int,
    val totalGames: Int,
    val activeGameList: List<GameSummary>,
    val recentGames: List<GameSummary>,
)

@Serializable
data class AdminBotsResponse(val bots: List<AdminBot>)

/** `POST /api/admin/bots`: the username and display name follow the rules of section 2.2. */
@Serializable
data class CreateBotRequest(val username: String, val displayName: String, val level: EngineLevel)

/** `PATCH /api/admin/bots/{username}`: a missing field is left alone; at least one is required. */
@Serializable
data class UpdateBotRequest(val level: EngineLevel? = null, val displayName: String? = null)

/** Where the effective value of a setting comes from. */
@Serializable
enum class SettingSource {
    @SerialName("db")
    DB,

    @SerialName("default")
    DEFAULT,
}

/** A runtime setting: its effective [value], its [defaultValue] (`default` in the JSON) and where the value comes from. */
@Serializable
data class SettingEntry(
    val key: String,
    val value: String,
    @SerialName("default") val defaultValue: String,
    val source: SettingSource,
    val description: String,
)

@Serializable
data class SettingsResponse(val settings: List<SettingEntry>)

/** `PUT /api/admin/settings/{key}`: the value as a string (a bare number or boolean is accepted too). */
@Serializable
data class SettingUpdateRequest(val value: JsonPrimitive)
