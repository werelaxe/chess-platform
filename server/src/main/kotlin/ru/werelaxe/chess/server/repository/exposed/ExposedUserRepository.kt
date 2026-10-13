package ru.werelaxe.chess.server.repository.exposed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.server.db.Users
import ru.werelaxe.chess.server.model.ProfileField
import ru.werelaxe.chess.server.model.User
import ru.werelaxe.chess.server.repository.UserRepository
import java.sql.SQLException
import java.time.Instant
import java.time.ZoneOffset

class ExposedUserRepository(private val db: Database) : UserRepository {
    override suspend fun create(username: String, passwordHash: String, createdAt: Instant): User? =
        tx { insert(username, passwordHash, isGuest = false, isBot = false, createdAt) }

    override suspend fun createGuest(username: String, createdAt: Instant): User? =
        tx { insert(username, passwordHash = null, isGuest = true, isBot = false, createdAt) }

    override suspend fun upsertBot(username: String, displayName: String, level: EngineLevel, createdAt: Instant): User? {
        if (tx { byUsername(username) } == null) {
            tx { insert(username, passwordHash = null, isGuest = false, isBot = true, createdAt, displayName, level) }
                ?.let { return it }
            // Another instance created the row first; it is brought up to date below like any existing one.
        }
        // Separate transactions on purpose: PostgreSQL aborts a transaction after a failed insert.
        return tx {
            val existing = byUsername(username)?.takeIf { it.isBot } ?: return@tx null
            if (existing.displayName == displayName && existing.botLevel == level) return@tx existing
            Users.update({ Users.id eq existing.id }) {
                it[Users.displayName] = displayName
                it[Users.botLevel] = level
            }
            existing.copy(displayName = displayName, botLevel = level)
        }
    }

    private fun insert(
        username: String,
        passwordHash: String?,
        isGuest: Boolean,
        isBot: Boolean,
        createdAt: Instant,
        displayName: String? = null,
        botLevel: EngineLevel? = null,
    ): User? {
        try {
            val id = Users.insert {
                it[Users.username] = username
                it[Users.usernameLower] = username.lowercase()
                it[Users.passwordHash] = passwordHash
                it[Users.isGuest] = isGuest
                it[Users.isBot] = isBot
                it[Users.createdAt] = createdAt.atOffset(ZoneOffset.UTC)
                it[Users.displayName] = displayName
                it[Users.botLevel] = botLevel
            }[Users.id]
            return User(id, username, passwordHash, isGuest, isBot, createdAt, locale = null, displayName, botLevel, lastSeenAt = null)
        } catch (e: SQLException) {
            if (isUniqueViolation(e)) return null else throw e
        }
    }

    override suspend fun findById(id: Long): User? = tx { byId(id) }

    override suspend fun findByUsername(username: String): User? = tx { byUsername(username) }

    override suspend fun findByIds(ids: Collection<Long>): Map<Long, User> {
        if (ids.isEmpty()) return emptyMap()
        return tx {
            Users.selectAll().where { Users.id inList ids.toSet() }.associate { it[Users.id] to it.toUser() }
        }
    }

    override suspend fun updateProfile(id: Long, fields: Map<ProfileField, String?>): User? = tx {
        if (fields.isNotEmpty()) {
            Users.update({ Users.id eq id }) { statement ->
                for ((field, value) in fields) {
                    when (field) {
                        ProfileField.LOCALE -> statement[Users.locale] = value
                        ProfileField.DISPLAY_NAME -> statement[Users.displayName] = value
                    }
                }
            }
        }
        byId(id)
    }

    override suspend fun touchLastSeen(id: Long, at: Instant) {
        tx { Users.update({ Users.id eq id }) { it[Users.lastSeenAt] = at.atOffset(ZoneOffset.UTC) } }
    }

    override suspend fun search(query: String, limit: Int, offset: Int): List<User> = tx {
        var condition: Op<Boolean> = Users.isGuest eq false
        if (query.isNotEmpty()) {
            val pattern = LikePattern("%", ESCAPE) + LikePattern.ofLiteral(query.lowercase(), ESCAPE) + "%"
            condition = condition and ((Users.usernameLower like pattern) or (Users.displayName.lowerCase() like pattern))
        }
        Users.selectAll()
            .where(condition)
            .orderBy(Users.usernameLower to SortOrder.ASC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toUser() }
    }

    override suspend fun listSeenSince(since: Instant, limit: Int): List<User> = tx {
        Users.selectAll()
            .where { (Users.isGuest eq false) and (Users.isBot eq false) and (Users.lastSeenAt greaterEq since.atOffset(ZoneOffset.UTC)) }
            .orderBy(Users.lastSeenAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toUser() }
    }

    private fun byId(id: Long): User? = Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUser()

    private fun byUsername(username: String): User? =
        Users.selectAll().where { Users.usernameLower eq username.lowercase() }.singleOrNull()?.toUser()

    private fun isUniqueViolation(e: SQLException): Boolean =
        generateSequence<Throwable>(e) { it.cause }.any { (it as? SQLException)?.sqlState == UNIQUE_VIOLATION }

    private suspend fun <T> tx(block: JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) {
        transaction(db) { block() }
    }

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
        const val ESCAPE = '\\'
    }
}
