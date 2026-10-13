package ru.werelaxe.chess.server.repository

import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.server.model.AccountCounts
import ru.werelaxe.chess.server.model.ProfileField
import ru.werelaxe.chess.server.model.User
import java.time.Instant

interface UserRepository {
    /** Inserts a registered user; returns null when the username is already taken (case-insensitively). */
    suspend fun create(username: String, passwordHash: String, createdAt: Instant): User?

    /** Inserts a guest, who has no password; returns null when the username is already taken. */
    suspend fun createGuest(username: String, createdAt: Instant): User?

    /** Inserts a bot, which has no password; returns null when the username is already taken. */
    suspend fun createBot(username: String, displayName: String, level: EngineLevel, createdAt: Instant): User?

    /** Every bot account, by id. */
    suspend fun listBots(): List<User>

    /**
     * Changes a bot's display name and/or level (null leaves one alone); returns the updated
     * bot, or null when [id] is not a bot's.
     */
    suspend fun updateBot(id: Long, displayName: String?, level: EngineLevel?): User?

    /** How many registered people, guests and bots exist. */
    suspend fun countAccounts(): AccountCounts

    suspend fun findById(id: Long): User?

    /** Case-insensitive lookup. */
    suspend fun findByUsername(username: String): User?

    suspend fun findByIds(ids: Collection<Long>): Map<Long, User>

    /** Changes the given profile fields (a null value clears one); returns the updated user, or null for an unknown id. */
    suspend fun updateProfile(id: Long, fields: Map<ProfileField, String?>): User?

    suspend fun touchLastSeen(id: Long, at: Instant)

    /**
     * Registered users and bots (never guests) whose username or display name contains [query]
     * case-insensitively (everybody for a blank query), by username.
     */
    suspend fun search(query: String, limit: Int, offset: Int): List<User>

    /** Registered people (no guests, no bots) seen since [since], most recently seen first. */
    suspend fun listSeenSince(since: Instant, limit: Int): List<User>
}
