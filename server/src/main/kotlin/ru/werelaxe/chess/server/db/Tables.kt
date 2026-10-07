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
    /** Null for guests and the computer player, who have no password. */
    val passwordHash = text("password_hash").nullable()
    val isGuest = bool("is_guest")
    val isBot = bool("is_bot")
    val createdAt = timestampWithTimeZone("created_at")
    /** The UI language chosen by the user (`en`, `ru`); null until set. */
    val locale = varchar("locale", 8).nullable()

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
    val moveCount = integer("move_count")
    val resultWinner = enumerationByName<Color>("result_winner", 8).nullable()
    val resultReason = enumerationByName<EndReason>("result_reason", 32).nullable()
    val drawOfferedBy = enumerationByName<Color>("draw_offered_by", 8).nullable()
    /** Set for games against the computer. */
    val botLevel = enumerationByName<EngineLevel>("bot_level", 8).nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
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
