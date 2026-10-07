package ru.werelaxe.chess.server

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import ru.werelaxe.chess.server.dto.ColorChoice
import ru.werelaxe.chess.server.dto.UserRef
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.repository.memory.InMemoryUserRepository
import ru.werelaxe.chess.server.service.AuthService
import ru.werelaxe.chess.server.service.JwtService
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GuestTest {
    @Test
    fun guestIsCreatedAndRecognisedByItsToken() = serverTest {
        val auth = guest()
        assertTrue(GUEST_NAME.matches(auth.user.username), auth.user.username)
        assertTrue(auth.user.guest)
        assertTrue(auth.token.isNotBlank())

        val me = client.get("/api/auth/me") { bearerAuth(auth.token) }
        assertEquals(HttpStatusCode.OK, me.status)
        assertEquals(auth.user, me.body<UserRef>())
    }

    @Test
    fun guestFlagIsOnlySerializedForGuests() = serverTest {
        val guest = guest()
        val alice = token("alice")
        val guestJson = client.get("/api/auth/me") { bearerAuth(guest.token) }.bodyAsText()
        val aliceJson = client.get("/api/auth/me") { bearerAuth(alice) }.bodyAsText()
        assertTrue("\"guest\":true" in guestJson, guestJson)
        assertFalse("guest" in aliceJson, aliceJson)
    }

    @Test
    fun guestsGetDistinctNames() = serverTest {
        val names = (1..5).map { guest().user.username }
        assertEquals(names.size, names.toSet().size, names.toString())
    }

    @Test
    fun guestCanCreateJoinAndPlay() = serverTest {
        val host = guest()
        val alice = token("alice")

        val created = createGame(host.token, color = ColorChoice.WHITE)
        assertEquals(host.user, created.white)
        assertEquals(host.user, created.creator)
        assertTrue(created.creator.guest)
        val joined = join(alice, created.id)
        assertEquals(GamePhase.ACTIVE, joined.status)
        assertEquals("alice", joined.black?.username)
        assertFalse(joined.black!!.guest)
        move(host.token, created.id, "e2e4")
        assertEquals(1, game(created.id).moveCount)

        val visitor = guest()
        val hosted = createGame(alice, color = ColorChoice.WHITE)
        val visited = join(visitor.token, hosted.id)
        assertEquals(visitor.user, visited.black)
        assertTrue(visited.black!!.guest)
        assertEquals(HttpStatusCode.OK, resign(visitor.token, hosted.id).status)
    }

    @Test
    fun guestNamesCannotBeLoggedInOrRegistered() = serverTest {
        val host = guest()
        login(host.user.username, TEST_PASSWORD).assertError(HttpStatusCode.Unauthorized, "invalid_credentials")
        login(host.user.username, "").assertError(HttpStatusCode.Unauthorized, "invalid_credentials")

        val reserved = register("Guest-123")
        reserved.assertError(HttpStatusCode.BadRequest, "validation")
        assertEquals("Names starting with guest- are reserved", reserved.error().message)
        register("GUEST-abcdef").assertError(HttpStatusCode.BadRequest, "validation")
        // The prefix alone is not reserved as a substring.
        assertEquals(HttpStatusCode.Created, register("my_guest").status)
    }

    @Test
    fun takenGuestNumberIsDrawnAgain() = runBlocking {
        // Both draws of the first scripted value map to the same number: the second one must be retried.
        val random = ScriptedRandom(7_000, 7_000, 9_000)
        val clock = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC)
        val jwt = JwtService("test-secret", Duration.ofDays(1), clock)
        val auth = AuthService(InMemoryUserRepository(), jwt, random, clock, bcryptCost = 4)

        val first = auth.createGuest().user
        val second = auth.createGuest().user
        assertNotEquals(first.username, second.username)
        assertTrue(GUEST_NAME.matches(first.username) && GUEST_NAME.matches(second.username))
        assertTrue(random.exhausted, "the collision consumed an extra draw")
    }

    /** A [Random] that hands out the given values in order; used to force a guest-name collision. */
    private class ScriptedRandom(vararg values: Int) : Random() {
        private val queue = ArrayDeque(values.toList())
        val exhausted: Boolean get() = queue.isEmpty()

        override fun nextBits(bitCount: Int): Int = queue.removeFirst()
    }

    private companion object {
        val GUEST_NAME = Regex("guest-[1-9][0-9]{5}")
    }
}
