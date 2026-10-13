package ru.werelaxe.chess.server.repository.memory

import kotlinx.coroutines.sync.withLock
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.server.model.AccountCounts
import ru.werelaxe.chess.server.model.ProfileField
import ru.werelaxe.chess.server.model.User
import ru.werelaxe.chess.server.repository.UserRepository
import java.time.Instant

/** Non-persistent repository for tests and local experiments. */
class InMemoryUserRepository(private val store: InMemoryStore = InMemoryStore()) : UserRepository {
    override suspend fun create(username: String, passwordHash: String, createdAt: Instant): User? =
        store.mutex.withLock { insert(username, passwordHash, isGuest = false, isBot = false, createdAt) }

    override suspend fun createGuest(username: String, createdAt: Instant): User? =
        store.mutex.withLock { insert(username, passwordHash = null, isGuest = true, isBot = false, createdAt) }

    override suspend fun createBot(username: String, displayName: String, level: EngineLevel, createdAt: Instant): User? =
        store.mutex.withLock { insert(username, passwordHash = null, isGuest = false, isBot = true, createdAt, displayName, level) }

    override suspend fun listBots(): List<User> = store.mutex.withLock {
        store.users.values.filter { it.isBot }.sortedBy { it.id }
    }

    override suspend fun updateBot(id: Long, displayName: String?, level: EngineLevel?): User? = store.mutex.withLock {
        val existing = store.users[id]?.takeIf { it.isBot } ?: return@withLock null
        existing.copy(displayName = displayName ?: existing.displayName, botLevel = level ?: existing.botLevel)
            .also { store.users[id] = it }
    }

    override suspend fun countAccounts(): AccountCounts = store.mutex.withLock {
        val accounts = store.users.values
        AccountCounts(
            users = accounts.count { !it.isGuest && !it.isBot },
            guests = accounts.count { it.isGuest },
            bots = accounts.count { it.isBot },
        )
    }

    /** Call under the store's lock. */
    private fun insert(
        username: String,
        passwordHash: String?,
        isGuest: Boolean,
        isBot: Boolean,
        createdAt: Instant,
        displayName: String? = null,
        botLevel: EngineLevel? = null,
    ): User? {
        val key = username.lowercase()
        if (key in store.userIdByLowerName) return null
        val user = User(
            id = store.nextUserId++,
            username = username,
            passwordHash = passwordHash,
            isGuest = isGuest,
            isBot = isBot,
            createdAt = createdAt,
            locale = null,
            displayName = displayName,
            botLevel = botLevel,
            lastSeenAt = null,
        )
        store.users[user.id] = user
        store.userIdByLowerName[key] = user.id
        return user
    }

    override suspend fun findById(id: Long): User? = store.mutex.withLock { store.users[id] }

    override suspend fun findByUsername(username: String): User? = store.mutex.withLock {
        store.userIdByLowerName[username.lowercase()]?.let { store.users[it] }
    }

    override suspend fun findByIds(ids: Collection<Long>): Map<Long, User> = store.mutex.withLock {
        ids.mapNotNull { id -> store.users[id]?.let { id to it } }.toMap()
    }

    override suspend fun updateProfile(id: Long, fields: Map<ProfileField, String?>): User? = store.mutex.withLock {
        var user = store.users[id] ?: return@withLock null
        for ((field, value) in fields) {
            user = when (field) {
                ProfileField.LOCALE -> user.copy(locale = value)
                ProfileField.DISPLAY_NAME -> user.copy(displayName = value)
            }
        }
        store.users[id] = user
        user
    }

    override suspend fun touchLastSeen(id: Long, at: Instant) {
        store.mutex.withLock { store.users[id]?.let { store.users[id] = it.copy(lastSeenAt = at) } }
    }

    override suspend fun search(query: String, limit: Int, offset: Int): List<User> = store.mutex.withLock {
        val needle = query.lowercase()
        store.users.values
            .filter { !it.isGuest }
            .filter { needle.isEmpty() || needle in it.username.lowercase() || it.displayName?.lowercase()?.contains(needle) == true }
            .sortedBy { it.username.lowercase() }
            .drop(offset)
            .take(limit)
    }

    override suspend fun listSeenSince(since: Instant, limit: Int): List<User> = store.mutex.withLock {
        store.users.values
            .filter { !it.isGuest && !it.isBot && it.lastSeenAt != null && it.lastSeenAt >= since }
            .sortedByDescending { it.lastSeenAt }
            .take(limit)
    }
}
