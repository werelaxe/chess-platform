package ru.werelaxe.chess.server.repository

import ru.werelaxe.chess.server.model.User
import java.time.Instant

interface UserRepository {
    /** Inserts a registered user; returns null when the username is already taken (case-insensitively). */
    suspend fun create(username: String, passwordHash: String, createdAt: Instant): User?

    /** Inserts a guest, who has no password; returns null when the username is already taken. */
    suspend fun createGuest(username: String, createdAt: Instant): User?

    suspend fun findById(id: Long): User?

    /** Case-insensitive lookup. */
    suspend fun findByUsername(username: String): User?

    suspend fun findByIds(ids: Collection<Long>): Map<Long, User>
}
