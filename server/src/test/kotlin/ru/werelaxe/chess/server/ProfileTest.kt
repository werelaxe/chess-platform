package ru.werelaxe.chess.server

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import ru.werelaxe.chess.server.dto.AuthResponse
import ru.werelaxe.chess.server.dto.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileTest {
    @Test
    fun registeredUserSetsAndReadsBackTheLocale() = serverTest {
        val alice = register("alice").body<AuthResponse>()
        assertNull(alice.user.locale)

        val updated = setLocale(alice.token, "ru")
        assertEquals(HttpStatusCode.OK, updated.status, updated.bodyAsString())
        assertEquals(UserProfile(alice.user.id, "alice", locale = "ru"), updated.body<UserProfile>())
        assertEquals("ru", me(alice.token).locale)
        // The choice lives with the account, so a fresh login returns it as well.
        assertEquals("ru", login("alice").body<AuthResponse>().user.locale)

        val cleared = setLocale(alice.token, null)
        assertEquals(HttpStatusCode.OK, cleared.status, cleared.bodyAsString())
        assertNull(cleared.body<UserProfile>().locale)
        assertNull(me(alice.token).locale)
    }

    @Test
    fun guestSetsAndReadsBackTheLocale() = serverTest {
        val guest = guest()
        assertNull(guest.user.locale)

        val updated = setLocale(guest.token, "en")
        assertEquals(HttpStatusCode.OK, updated.status, updated.bodyAsString())
        val expected = UserProfile(guest.user.id, guest.user.username, guest = true, locale = "en")
        assertEquals(expected, updated.body<UserProfile>())
        assertEquals(expected, me(guest.token))
    }

    @Test
    fun localeIsPerUser() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        assertEquals(HttpStatusCode.OK, setLocale(alice, "ru").status)
        assertEquals("ru", me(alice).locale)
        assertNull(me(bob).locale)
    }

    @Test
    fun unsetLocaleIsEncodedAsNull() = serverTest {
        val alice = token("alice")
        val json = client.get("/api/auth/me") { bearerAuth(alice) }.bodyAsText()
        assertTrue("\"locale\":null" in json, json)
    }

    @Test
    fun unsupportedLocaleIsRejected() = serverTest {
        val alice = token("alice")
        for (value in listOf("\"de\"", "\"EN\"", "\"ru \"", "\"\"", "42", "true", "[\"ru\"]")) {
            patchMe(alice, "{\"locale\":$value}").assertError(HttpStatusCode.BadRequest, "validation")
        }
        // The field itself is required: an empty patch is not a way to clear the locale.
        patchMe(alice, "{}").assertError(HttpStatusCode.BadRequest, "validation")
        patchMe(alice, "not json").assertError(HttpStatusCode.BadRequest, "validation")
        assertNull(me(alice).locale)
    }

    @Test
    fun localeRequiresAuthentication() = serverTest {
        client.patch("/api/auth/me") {
            contentType(ContentType.Application.Json)
            setBody("{\"locale\":\"ru\"}")
        }.assertError(HttpStatusCode.Unauthorized, "unauthorized")
        patchMe("not-a-token", "{\"locale\":\"ru\"}").assertError(HttpStatusCode.Unauthorized, "unauthorized")
    }

    private suspend fun TestContext.patchMe(token: String, body: String): HttpResponse =
        client.patch("/api/auth/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
}
