package ru.werelaxe.chess.server.service

import at.favre.lib.crypto.bcrypt.BCrypt
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.werelaxe.chess.server.dto.AuthResponse
import ru.werelaxe.chess.server.dto.UserRef
import ru.werelaxe.chess.server.model.User
import ru.werelaxe.chess.server.repository.UserRepository
import java.time.Clock

class AuthService(
    private val users: UserRepository,
    private val jwt: JwtService,
    private val clock: Clock,
    private val bcryptCost: Int = 12,
) {
    /** bcrypt is deliberately slow; its own bounded dispatcher keeps it from starving the rest of the server. */
    private val hashing = Dispatchers.Default.limitedParallelism(HASHING_PARALLELISM)

    suspend fun register(username: String, password: String): AuthResponse {
        validateUsername(username)
        if (!hasValidLength(password)) {
            throw ApiException.validation("Password must be $MIN_PASSWORD_LENGTH-$MAX_PASSWORD_LENGTH characters")
        }
        val hash = withContext(hashing) {
            BCrypt.withDefaults().hashToString(bcryptCost, password.toCharArray())
        }
        val user = users.create(username, hash, clock.instant())
            ?: throw ApiException(HttpStatusCode.Conflict, "username_taken", "This username is already taken")
        return AuthResponse(jwt.issue(user), UserRef.of(user))
    }

    suspend fun login(username: String, password: String): AuthResponse {
        val user = users.findByUsername(username) ?: throw invalidCredentials()
        // A password outside the accepted range cannot match any stored hash; this also keeps
        // over-long input away from bcrypt, which rejects it with its own message.
        if (!hasValidLength(password)) throw invalidCredentials()
        val verified = withContext(hashing) {
            BCrypt.verifyer().verify(password.toCharArray(), user.passwordHash).verified
        }
        if (!verified) throw invalidCredentials()
        return AuthResponse(jwt.issue(user), UserRef.of(user))
    }

    /** The current user; fails when the account behind a valid token no longer exists. */
    suspend fun me(principal: UserPrincipal): User =
        users.findById(principal.id) ?: throw ApiException.unauthorized("Unknown user")

    private fun invalidCredentials() =
        ApiException(HttpStatusCode.Unauthorized, "invalid_credentials", "Invalid username or password")

    private fun validateUsername(username: String) {
        if (!USERNAME_PATTERN.matches(username)) {
            throw ApiException.validation("Username must be 3-20 characters: letters, digits or underscore")
        }
    }

    private fun hasValidLength(password: String): Boolean =
        password.length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH &&
            password.toByteArray(Charsets.UTF_8).size <= MAX_PASSWORD_LENGTH

    private companion object {
        val USERNAME_PATTERN = Regex("[A-Za-z0-9_]{3,20}")
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_LENGTH = 72
        const val HASHING_PARALLELISM = 2
    }
}
