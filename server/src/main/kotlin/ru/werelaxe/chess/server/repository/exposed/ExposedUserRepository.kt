package ru.werelaxe.chess.server.repository.exposed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import ru.werelaxe.chess.server.db.Users
import ru.werelaxe.chess.server.model.User
import ru.werelaxe.chess.server.repository.UserRepository
import java.sql.SQLException
import java.time.Instant
import java.time.ZoneOffset

class ExposedUserRepository(private val db: Database) : UserRepository {
    override suspend fun create(username: String, passwordHash: String, createdAt: Instant): User? =
        insert(username, passwordHash, isGuest = false, isBot = false, createdAt)

    override suspend fun createGuest(username: String, createdAt: Instant): User? =
        insert(username, passwordHash = null, isGuest = true, isBot = false, createdAt)

    override suspend fun createBot(username: String, createdAt: Instant): User? =
        insert(username, passwordHash = null, isGuest = false, isBot = true, createdAt)

    private suspend fun insert(
        username: String,
        passwordHash: String?,
        isGuest: Boolean,
        isBot: Boolean,
        createdAt: Instant,
    ): User? = tx {
        try {
            val id = Users.insert {
                it[Users.username] = username
                it[Users.usernameLower] = username.lowercase()
                it[Users.passwordHash] = passwordHash
                it[Users.isGuest] = isGuest
                it[Users.isBot] = isBot
                it[Users.createdAt] = createdAt.atOffset(ZoneOffset.UTC)
            }[Users.id]
            User(id, username, passwordHash, isGuest, isBot, createdAt, locale = null)
        } catch (e: SQLException) {
            if (isUniqueViolation(e)) null else throw e
        }
    }

    override suspend fun findById(id: Long): User? = tx {
        Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUser()
    }

    override suspend fun findByUsername(username: String): User? = tx {
        Users.selectAll().where { Users.usernameLower eq username.lowercase() }.singleOrNull()?.toUser()
    }

    override suspend fun findByIds(ids: Collection<Long>): Map<Long, User> {
        if (ids.isEmpty()) return emptyMap()
        return tx {
            Users.selectAll().where { Users.id inList ids.toSet() }.associate { it[Users.id] to it.toUser() }
        }
    }

    override suspend fun updateLocale(id: Long, locale: String?): User? = tx {
        Users.update({ Users.id eq id }) { it[Users.locale] = locale }
        Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUser()
    }

    private fun isUniqueViolation(e: SQLException): Boolean =
        generateSequence<Throwable>(e) { it.cause }.any { (it as? SQLException)?.sqlState == UNIQUE_VIOLATION }

    private fun ResultRow.toUser() = User(
        id = this[Users.id],
        username = this[Users.username],
        passwordHash = this[Users.passwordHash],
        isGuest = this[Users.isGuest],
        isBot = this[Users.isBot],
        createdAt = this[Users.createdAt].toInstant(),
        locale = this[Users.locale],
    )

    private suspend fun <T> tx(block: JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) {
        transaction(db) { block() }
    }

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
    }
}
