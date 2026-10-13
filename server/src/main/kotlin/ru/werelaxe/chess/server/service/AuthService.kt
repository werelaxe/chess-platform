package ru.werelaxe.chess.server.service

import at.favre.lib.crypto.bcrypt.BCrypt
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.werelaxe.chess.server.dto.AuthResponse
import ru.werelaxe.chess.server.dto.PlayerRatings
import ru.werelaxe.chess.server.dto.UserProfile
import ru.werelaxe.chess.server.model.ProfileField
import ru.werelaxe.chess.server.model.User
import ru.werelaxe.chess.server.repository.RatingRepository
import ru.werelaxe.chess.server.repository.UserRepository
import java.time.Clock
import kotlin.random.Random

class AuthService(
    private val users: UserRepository,
    private val ratings: RatingRepository,
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
        return AuthResponse(jwt.issue(user), profile(user))
    }

    suspend fun login(username: String, password: String): AuthResponse {
        val user = users.findByUsername(username) ?: throw invalidCredentials()
        // Guests and bots have no password, so their names cannot be logged into at all.
        val hash = user.passwordHash ?: throw invalidCredentials()
        // A password outside the accepted range cannot match any stored hash; this also keeps
        // over-long input away from bcrypt, which rejects it with its own message.
        if (!hasValidLength(password)) throw invalidCredentials()
        val verified = withContext(hashing) {
            BCrypt.verifyer().verify(password.toCharArray(), hash).verified
        }
        if (!verified) throw invalidCredentials()
        return AuthResponse(jwt.issue(user), profile(user))
    }

    /**
     * Creates a `guest-NNNNNN` account with a random six-digit number and no password; the
     * token is the only way to act as it. A number already in use is simply drawn again.
     */
    suspend fun createGuest(): AuthResponse {
        repeat(MAX_GUEST_ATTEMPTS) {
            val number = random.nextInt(MIN_GUEST_NUMBER, MAX_GUEST_NUMBER + 1)
            val user = users.createGuest("$GUEST_PREFIX$number", clock.instant()) ?: return@repeat
            return AuthResponse(jwt.issue(user), profile(user))
        }
        throw IllegalStateException("No free guest name after $MAX_GUEST_ATTEMPTS attempts")
    }

    /** The current user; fails when the account behind a valid token no longer exists. */
    suspend fun me(principal: UserPrincipal): User =
        users.findById(principal.id) ?: throw ApiException.unauthorized("Unknown user")

    /** The own profile of [user] with its ratings (guests have none). */
    suspend fun profile(user: User): UserProfile {
        val ratings = if (user.isGuest) PlayerRatings.NONE else PlayerRatings.of(ratings.forUsers(listOf(user.id)))
        return UserProfile.of(user, ratings)
    }

    /**
     * Changes the given profile fields; a null value clears a field. The locale must be a
     * supported one and the display name, trimmed, 1 to [MAX_DISPLAY_NAME_LENGTH] characters
     * with at least one letter or digit and nothing invisible (see [validateDisplayName]).
     */
    suspend fun updateProfile(principal: UserPrincipal, fields: Map<ProfileField, String?>): User {
        if (fields.isEmpty()) throw ApiException.validation("Nothing to update: expected locale and/or displayName")
        val validated = fields.mapValues { (field, value) ->
            when (field) {
                ProfileField.LOCALE -> value?.also {
                    if (it !in SUPPORTED_LOCALES) {
                        throw ApiException.validation(
                            "Locale must be one of ${SUPPORTED_LOCALES.joinToString()}, or null to clear it",
                        )
                    }
                }
                ProfileField.DISPLAY_NAME -> value?.trim()?.also { validateDisplayName(it) }
            }
        }
        return users.updateProfile(principal.id, validated) ?: throw ApiException.unauthorized("Unknown user")
    }

    /**
     * A display name is shown wherever the username is, so it must be visible and harmless:
     * no control characters (PostgreSQL rejects NUL outright), no formatting characters such as
     * zero-width spaces or bidirectional overrides, no line or paragraph separators, and at
     * least one letter or digit so that it cannot be blank once rendered.
     */
    private fun validateDisplayName(name: String) {
        if (name.isEmpty() || name.length > MAX_DISPLAY_NAME_LENGTH) {
            throw ApiException.validation("Display name must be 1-$MAX_DISPLAY_NAME_LENGTH characters, or null to clear it")
        }
        val codePoints = name.codePoints().toArray()
        if (codePoints.any { isInvisible(it) }) {
            throw ApiException.validation("Display name must not contain control or formatting characters")
        }
        if (codePoints.none { Character.isLetterOrDigit(it) }) {
            throw ApiException.validation("Display name must contain at least one letter or digit")
        }
    }

    /** Control characters and the categories Cf, Zl and Zp (U+200B..U+200F, U+202A..U+202E, U+2066..U+2069 are Cf). */
    private fun isInvisible(codePoint: Int): Boolean =
        Character.isISOControl(codePoint) || Character.getType(codePoint).toByte() in INVISIBLE_CATEGORIES

    private fun invalidCredentials() =
        ApiException(HttpStatusCode.Unauthorized, "invalid_credentials", "Invalid username or password")

    private fun validateUsername(username: String) {
        // Checked before the pattern so that the reason is the reservation, not the hyphen.
        if (username.startsWith(GUEST_PREFIX, ignoreCase = true)) {
            throw ApiException.validation("Names starting with $GUEST_PREFIX are reserved")
        }
        // Path segments of /api/players and /api/auth: a user so named could never be looked up.
        if (username.lowercase() in RESERVED_USERNAMES) {
            throw ApiException.validation("The name '$username' is reserved")
        }
        if (!USERNAME_PATTERN.matches(username)) {
            throw ApiException.validation("Username must be 3-20 characters: letters, digits or underscore")
        }
    }

    private fun hasValidLength(password: String): Boolean =
        password.length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH &&
            password.toByteArray(Charsets.UTF_8).size <= MAX_PASSWORD_LENGTH

    companion object {
        const val MAX_DISPLAY_NAME_LENGTH = 40
        private val USERNAME_PATTERN = Regex("[A-Za-z0-9_]{3,20}")

        /** Literal segments under `/api/players` and `/api/auth` that would shadow a profile of that name. */
        private val RESERVED_USERNAMES = setOf("leaderboard", "me", "bots")
        private val INVISIBLE_CATEGORIES = setOf(Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR)
        private const val MIN_PASSWORD_LENGTH = 8
        private const val MAX_PASSWORD_LENGTH = 72
        private const val HASHING_PARALLELISM = 2
        private const val GUEST_PREFIX = "guest-"
        private const val MIN_GUEST_NUMBER = 100_000
        private const val MAX_GUEST_NUMBER = 999_999
        private const val MAX_GUEST_ATTEMPTS = 20
        /** The languages the web client is translated into. */
        private val SUPPORTED_LOCALES = setOf("en", "ru")
    }
}
