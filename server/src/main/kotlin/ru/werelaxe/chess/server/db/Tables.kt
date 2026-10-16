package ru.werelaxe.chess.server.db

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.model.Visibility

/** Exposed mapping of the schema created by the Flyway migrations. */
object Users : Table("users") {
    val id = long("id").autoIncrement()
    val username = varchar("username", 20)
    val usernameLower = varchar("username_lower", 20).uniqueIndex()
    /** Null for guests and bots, who have no password. */
    val passwordHash = text("password_hash").nullable()
    val isGuest = bool("is_guest")
    val isBot = bool("is_bot")
    val createdAt = timestampWithTimeZone("created_at")
    /** The UI language chosen by the user (`en`, `ru`); null until set. */
    val locale = varchar("locale", 8).nullable()
    /** The optional name shown next to the username. */
    val displayName = varchar("display_name", 40).nullable()
    /** The strength of a bot; null for people. */
    val botLevel = enumerationByName<EngineLevel>("bot_level", 8).nullable()
    /** The last authenticated request, refreshed at most every few minutes; null until then. */
    val lastSeenAt = timestampWithTimeZone("last_seen_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object Games : Table("games") {
    val id = varchar("id", 16)
    val kind = enumerationByName<GameKind>("kind", 16)
    val visibility = enumerationByName<Visibility>("visibility", 16)
    val status = enumerationByName<GamePhase>("status", 16)
    val creatorId = long("creator_id").references(Users.id)
    val whiteId = long("white_id").references(Users.id).nullable()
    val blackId = long("black_id").references(Users.id).nullable()
    /** The only account allowed to join while WAITING; null for a game open to anybody. */
    val inviteeId = long("invitee_id").references(Users.id).nullable()
    val rated = bool("rated")
    val moveCount = integer("move_count")
    val resultWinner = enumerationByName<Color>("result_winner", 8).nullable()
    val resultReason = enumerationByName<EndReason>("result_reason", 32).nullable()
    val drawOfferedBy = enumerationByName<Color>("draw_offered_by", 8).nullable()
    /** The players' ratings when the game became ACTIVE; null for guests and before that. */
    val whiteRatingBefore = integer("white_rating_before").nullable()
    val blackRatingBefore = integer("black_rating_before").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    /** The latest move, or the start of the game before the first one; null while WAITING. */
    val lastMoveAt = timestampWithTimeZone("last_move_at").nullable()
    val finishedAt = timestampWithTimeZone("finished_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object Moves : Table("moves") {
    val gameId = varchar("game_id", 16).references(Games.id, onDelete = ReferenceOption.CASCADE)
    val ply = integer("ply")
    val move = registerColumn("move", JsonbColumnType())
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(gameId, ply)
}

/** One row per user and variant, created by the first rated game. */
object Ratings : Table("ratings") {
    val userId = long("user_id").references(Users.id)
    val kind = enumerationByName<GameKind>("kind", 16)
    val rating = integer("rating")
    val games = integer("games")
    val wins = integer("wins")
    val draws = integer("draws")
    val losses = integer("losses")
    val peak = integer("peak")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(userId, kind)
}

/** Every rating update, two rows per rated game. */
object RatingChanges : Table("rating_changes") {
    val id = long("id").autoIncrement()
    val gameId = varchar("game_id", 16).references(Games.id, onDelete = ReferenceOption.CASCADE)
    val userId = long("user_id").references(Users.id)
    val kind = enumerationByName<GameKind>("kind", 16)
    val ratingBefore = integer("rating_before")
    val ratingAfter = integer("rating_after")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

/** Runtime settings by key (docs/ARCHITECTURE.md section 2.11); a key without a row has its default. */
object Settings : Table("settings") {
    val key = varchar("key", 64)
    val value = text("value")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(key)
}
