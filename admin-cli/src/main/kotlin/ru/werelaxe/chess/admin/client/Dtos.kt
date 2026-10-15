package ru.werelaxe.chess.admin.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * The client's view of the admin API (docs/ARCHITECTURE.md section 2.11): only the fields the
 * tool shows, decoded with [AdminJson] so that anything else the server sends is ignored.
 */
val AdminJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = false
}

/** The strength of a bot; the names are the server's `EngineLevel`. */
enum class BotLevel {
    NOVICE,
    EASY,
    MEDIUM,
    HARD,
    EXPERT,
}

@Serializable
data class UserRef(
    val username: String,
    val name: String? = null,
    val botLevel: String? = null,
    val rating: Int? = null,
)

@Serializable
data class RatingSummary(
    val rating: Int,
    val games: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val peak: Int,
)

@Serializable
data class PlayerRatings(
    @SerialName("CLASSIC") val classic: RatingSummary? = null,
    @SerialName("QUANTUM") val quantum: RatingSummary? = null,
)

/** The phase of a game; the names are the server's `GamePhase`. */
enum class GameStatus {
    WAITING,
    ACTIVE,
    FINISHED,
}

@Serializable
data class GameResult(val winner: String? = null, val reason: String)

@Serializable
data class GameSummary(
    val id: String,
    val kind: String,
    val status: String,
    val rated: Boolean = true,
    val white: UserRef? = null,
    val black: UserRef? = null,
    val moveCount: Int = 0,
    val result: GameResult? = null,
    val createdAt: String,
    val updatedAt: String,
)

/** A bot as the admin API lists it: a `PlayerSummary` plus the bot fields. */
@Serializable
data class AdminBot(
    val user: UserRef,
    val ratings: PlayerRatings = PlayerRatings(),
    val level: String,
    val legacy: Boolean = false,
    val activeGames: Int,
    val totalGames: Int,
)

/** [AdminBot] plus the games of `GET /api/admin/bots/{username}`. */
@Serializable
data class AdminBotDetails(
    val user: UserRef,
    val ratings: PlayerRatings = PlayerRatings(),
    val level: String,
    val legacy: Boolean = false,
    val activeGames: Int,
    val totalGames: Int,
    val activeGameList: List<GameSummary> = emptyList(),
    val recentGames: List<GameSummary> = emptyList(),
)

@Serializable
data class BotList(val bots: List<AdminBot>)

@Serializable
data class GameList(val games: List<GameSummary>)

@Serializable
data class SpawnBotRequest(val username: String, val displayName: String, val level: BotLevel)

/** A missing field leaves the bot's value alone. */
@Serializable
data class UpdateBotRequest(val level: BotLevel? = null, val displayName: String? = null)

@Serializable
data class GameCounts(val waiting: Int, val active: Int, val finished: Int)

/** [humanGames] are the games in progress with a human seat; [settings] is the server's key-value map, shown as it comes. */
@Serializable
data class Overview(
    val users: Int,
    val guests: Int,
    val bots: Int,
    val games: GameCounts,
    val activeBotGames: Int,
    val humanGames: Int,
    val settings: JsonObject = JsonObject(emptyMap()),
)

/** [source] is `db` when the value was set through the API and `default` otherwise. */
@Serializable
data class Setting(
    val key: String,
    val value: String,
    @SerialName("default") val defaultValue: String,
    val source: String,
    val description: String = "",
)

@Serializable
data class SettingList(val settings: List<Setting>)

@Serializable
data class SettingValue(val value: String)

@Serializable
data class ErrorResponse(val error: String, val message: String)
