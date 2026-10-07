package ru.werelaxe.chess.server.service

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Payload
import ru.werelaxe.chess.server.model.User
import java.time.Clock
import java.time.Duration

/** Issues and verifies HS256 tokens with `sub` = user id plus `username` and `guest` claims. */
class JwtService(secret: String, private val ttl: Duration, private val clock: Clock) {
    private val algorithm = Algorithm.HMAC256(secret)

    /** Verifies against the injected clock so that tests can use a fixed time. */
    val verifier: JWTVerifier = (JWT.require(algorithm) as JWTVerifier.BaseVerification).build(clock)

    fun issue(user: User): String {
        val now = clock.instant()
        return JWT.create()
            .withSubject(user.id.toString())
            .withClaim(USERNAME_CLAIM, user.username)
            .withClaim(GUEST_CLAIM, user.isGuest)
            .withIssuedAt(now)
            .withExpiresAt(now.plus(ttl))
            .sign(algorithm)
    }

    fun principal(payload: Payload): UserPrincipal? {
        val id = payload.subject?.toLongOrNull() ?: return null
        val username = payload.getClaim(USERNAME_CLAIM).asString() ?: return null
        // Tokens issued before guests existed have no claim and belong to registered users.
        val guest = payload.getClaim(GUEST_CLAIM).asBoolean() ?: false
        return UserPrincipal(id, username, guest)
    }

    private companion object {
        const val USERNAME_CLAIM = "username"
        const val GUEST_CLAIM = "guest"
    }
}
