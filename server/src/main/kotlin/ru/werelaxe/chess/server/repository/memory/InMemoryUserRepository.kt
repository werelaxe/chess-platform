package ru.werelaxe.chess.server.repository.memory

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.werelaxe.chess.server.model.User
import ru.werelaxe.chess.server.repository.UserRepository
import java.time.Instant

/** Non-persistent repository for tests and local experiments. */
class InMemoryUserRepository : UserRepository {
    private val mutex = Mutex()
    private val byId = LinkedHashMap<Long, User>()
    private val idByLowerName = HashMap<String, Long>()
    private var nextId = 1L

    override suspend fun create(username: String, passwordHash: String, createdAt: Instant): User? =
        insert(username, passwordHash, isGuest = false, createdAt)

    override suspend fun createGuest(username: String, createdAt: Instant): User? =
        insert(username, passwordHash = null, isGuest = true, createdAt)

    private suspend fun insert(username: String, passwordHash: String?, isGuest: Boolean, createdAt: Instant): User? =
        mutex.withLock {
            val key = username.lowercase()
            if (key in idByLowerName) return null
            val user = User(nextId++, username, passwordHash, isGuest, createdAt)
            byId[user.id] = user
            idByLowerName[key] = user.id
            user
        }

    override suspend fun findById(id: Long): User? = mutex.withLock { byId[id] }

    override suspend fun findByUsername(username: String): User? = mutex.withLock {
        idByLowerName[username.lowercase()]?.let { byId[it] }
    }

    override suspend fun findByIds(ids: Collection<Long>): Map<Long, User> = mutex.withLock {
        ids.mapNotNull { id -> byId[id]?.let { id to it } }.toMap()
    }
}
