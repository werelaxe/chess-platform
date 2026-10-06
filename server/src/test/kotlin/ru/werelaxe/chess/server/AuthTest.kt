package ru.werelaxe.chess.server

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import ru.werelaxe.chess.server.dto.AuthResponse
import ru.werelaxe.chess.server.dto.HealthResponse
import ru.werelaxe.chess.server.dto.UserRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthTest {
    @Test
    fun healthEndpoint() = serverTest {
        val response = client.get("/api/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.body<HealthResponse>().status)
    }

    @Test
    fun registerLoginAndMe() = serverTest {
        val registered = register("Alice")
        assertEquals(HttpStatusCode.Created, registered.status)
        val auth = registered.body<AuthResponse>()
        assertEquals("Alice", auth.user.username)
        assertTrue(auth.token.isNotBlank())

        val loggedIn = login("alice")
        assertEquals(HttpStatusCode.OK, loggedIn.status)
        val loginAuth = loggedIn.body<AuthResponse>()
        assertEquals(auth.user, loginAuth.user)

        val me = client.get("/api/auth/me") { bearerAuth(loginAuth.token) }
        assertEquals(HttpStatusCode.OK, me.status)
        assertEquals(UserRef(auth.user.id, "Alice"), me.body<UserRef>())
    }

    @Test
    fun validationErrors() = serverTest {
        register("ab").assertError(HttpStatusCode.BadRequest, "validation")
        register("has space").assertError(HttpStatusCode.BadRequest, "validation")
        register("x".repeat(21)).assertError(HttpStatusCode.BadRequest, "validation")
        register("alice", "short").assertError(HttpStatusCode.BadRequest, "validation")
        register("alice", "p".repeat(73)).assertError(HttpStatusCode.BadRequest, "validation")

        assertEquals(HttpStatusCode.Created, register("alice").status)
        register("ALICE").assertError(HttpStatusCode.Conflict, "username_taken")
    }

    @Test
    fun badCredentialsAndMissingToken() = serverTest {
        token("alice")
        login("alice", "wrong-password").assertError(HttpStatusCode.Unauthorized, "invalid_credentials")
        login("nobody").assertError(HttpStatusCode.Unauthorized, "invalid_credentials")

        client.get("/api/auth/me").assertError(HttpStatusCode.Unauthorized, "unauthorized")
        client.get("/api/auth/me") { bearerAuth("not-a-token") }.assertError(HttpStatusCode.Unauthorized, "unauthorized")
    }

    @Test
    fun malformedJsonIsAValidationError() = serverTest {
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("{\"username\": 42}")
        }
        response.assertError(HttpStatusCode.BadRequest, "validation")
    }

    @Test
    fun unknownRouteIsJson404() = serverTest {
        client.get("/api/nope").assertError(HttpStatusCode.NotFound, "not_found")
    }
}
