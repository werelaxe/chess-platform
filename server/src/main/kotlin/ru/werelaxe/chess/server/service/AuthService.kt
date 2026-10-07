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
import kotlin.random.Random

class AuthService(
    private val users: UserRepository,
    private val jwt: JwtService,
    private val random: Random,
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
        // Guests have no password, so their names cannot be logged into at all.
        val hash = user.passwordHash ?: throw invalidCredentials()
        // A password outside the accepted range cannot match any stored hash; this also keeps
        // over-long input away from bcrypt, which rejects it with its own message.
        if (!hasValidLength(password)) throw invalidCredentials()
        val verified = withContext(hashing) {
            BCrypt.verifyer().verify(password.toCharArray(), hash).verified
        }
        if (!verified) throw invalidCredentials()
        return AuthResponse(jwt.issue(user), UserRef.of(user))
    }

    /**
     * Creates a `guest-NNNNNN` account with a random six-digit number and no password; the
     * token is the only way to act as it. A number already in use is simply drawn again.
     */
    suspend fun createGuest(): AuthResponse {
        repeat(MAX_GUEST_ATTEMPTS) {
            val number = random.nextInt(MIN_GUEST_NUMBER, MAX_GUEST_NUMBER + 1)
            val user = users.createGuest("$GUEST_PREFIX$number", clock.instant()) ?: return@repeat
            return AuthResponse(jwt.issue(user), UserRef.of(user))
        }
        throw IllegalStateException("No free guest name after $MAX_GUEST_ATTEMPTS attempts")
    }

    /** The current user; fails when the account behind a valid token no longer exists. */
    suspend fun me(principal: UserPrincipal): User =
        users.findById(principal.id) ?: throw ApiException.unauthorized("Unknown user")

    private fun invalidCredentials() =
        ApiException(HttpStatusCode.Unauthorized, "invalid_credentials", "Invalid username or password")

    private fun validateUsername(username: String) {
        // Checked before the pattern so that the reason is the reservation, not the hyphen.
        if (username.startsWith(GUEST_PREFIX, ignoreCase = true)) {
            throw ApiException.validation("Names starting with $GUEST_PREFIX are reserved")
        }
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
        const val GUEST_PREFIX = "guest-"
        const val MIN_GUEST_NUMBER = 100_000
        const val MAX_GUEST_NUMBER = 999_999
        const val MAX_GUEST_ATTEMPTS = 20
    }
}
