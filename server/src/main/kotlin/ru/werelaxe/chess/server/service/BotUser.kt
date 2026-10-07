package ru.werelaxe.chess.server.service

import ru.werelaxe.chess.server.model.User
import ru.werelaxe.chess.server.repository.UserRepository
import java.time.Clock

/**
 * The system account that holds the computer's seat in games against it. It has no password,
 * so nobody can log in as it, and its name cannot be registered because the row exists.
 */
object BotUser {
    const val USERNAME = "computer"

    /**
     * Creates the account unless it exists. Fails when a person registered the name before it
     * was reserved: that account has to be renamed before the computer can play.
     */
    suspend fun ensure(users: UserRepository, clock: Clock): User {
        val existing = users.findByUsername(USERNAME)
        if (existing != null) {
            check(existing.isBot) {
                "A regular account holds the username '$USERNAME'; rename it so that the computer player can be created"
            }
            return existing
        }
        // A null here means another server instance created the row first.
        return users.createBot(USERNAME, clock.instant())
            ?: checkNotNull(users.findByUsername(USERNAME)?.takeIf { it.isBot }) {
                "Could not create the '$USERNAME' account"
            }
    }
}
