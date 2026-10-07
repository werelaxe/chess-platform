package ru.werelaxe.chess.server.repository

import ru.werelaxe.chess.server.model.User
import java.time.Instant

interface UserRepository {
    /** Inserts a registered user; returns null when the username is already taken (case-insensitively). */
    suspend fun create(username: String, passwordHash: String, createdAt: Instant): User?

    /** Inserts a guest, who has no password; returns null when the username is already taken. */
    suspend fun createGuest(username: String, createdAt: Instant): User?

    /** Inserts the computer player, which has no password; returns null when the username is already taken. */
    suspend fun createBot(username: String, createdAt: Instant): User?

    suspend fun findById(id: Long): User?

    /** Case-insensitive lookup. */
    suspend fun findByUsername(username: String): User?

    suspend fun findByIds(ids: Collection<Long>): Map<Long, User>

    /** Stores the UI language of the user (null clears it); returns the updated user, or null for an unknown id. */
    suspend fun updateLocale(id: Long, locale: String?): User?
}
