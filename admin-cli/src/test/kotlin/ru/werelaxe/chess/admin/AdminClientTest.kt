package ru.werelaxe.chess.admin

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import ru.werelaxe.chess.admin.client.AdminApiException
import ru.werelaxe.chess.admin.client.AdminClient
import ru.werelaxe.chess.admin.client.BotLevel
import ru.werelaxe.chess.admin.client.GameStatus
import ru.werelaxe.chess.admin.client.SpawnBotRequest
import ru.werelaxe.chess.admin.client.UpdateBotRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The client against a fake of the admin endpoints: paths, methods, the token header and the bodies. */
class AdminClientTest {
    private val api = FakeAdminApi()

    private fun client(baseUrl: String = "http://api.test:8080") = AdminClient(api.engine(), baseUrl, FakeAdminApi.TOKEN)

    @Test
    fun listsTheBotsWithTheToken() = runBlocking {
        val reply = client().use { it.bots() }
        val request = api.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("http://api.test:8080/api/admin/bots", request.url)
        assertEquals(FakeAdminApi.TOKEN, request.token)
        assertEquals("", request.body)
        assertEquals(Fixtures.BOTS, reply.raw)
        assertEquals(listOf("novice-nick", "computer"), reply.value.bots.map { it.user.username })
        assertEquals(1480, reply.value.bots[0].ratings.classic?.rating)
        assertNull(reply.value.bots[0].ratings.quantum)
        assertTrue(reply.value.bots[1].legacy)
    }

    @Test
    fun spawnPostsTheBot() = runBlocking {
        val reply = client("http://api.test:8080/").use {
            it.spawnBot(SpawnBotRequest("hard-harry", "Harry the Hard", BotLevel.HARD))
        }
        val request = api.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("http://api.test:8080/api/admin/bots", request.url)
        assertEquals(FakeAdminApi.TOKEN, request.token)
        assertEquals(ContentType.Application.Json.toString(), request.contentType)
        assertEquals("""{"username":"hard-harry","displayName":"Harry the Hard","level":"HARD"}""", request.body)
        assertEquals("hard-harry", reply.value.user.username)
        assertEquals("HARD", reply.value.level)
    }

    @Test
    fun updateSendsOnlyTheGivenFields() = runBlocking {
        client().use { it.updateBot("novice-nick", UpdateBotRequest(level = BotLevel.EASY)) }
        client().use { it.updateBot("novice-nick", UpdateBotRequest(displayName = "Nick")) }
        assertEquals(listOf(HttpMethod.Patch, HttpMethod.Patch), api.requests.map { it.method })
        assertEquals("http://api.test:8080/api/admin/bots/novice-nick", api.requests[0].url)
        assertEquals("""{"level":"EASY"}""", api.requests[0].body)
        assertEquals("""{"displayName":"Nick"}""", api.requests[1].body)
    }

    @Test
    fun showsABotByUsername() = runBlocking {
        val reply = client().use { it.bot("novice-nick") }
        assertEquals("http://api.test:8080/api/admin/bots/novice-nick", api.requests.single().url)
        assertEquals(1, reply.value.activeGameList.size)
        assertEquals(2, reply.value.recentGames.size)
        assertEquals("CHECKMATE", reply.value.recentGames[1].result?.reason)
    }

    @Test
    fun listsTheGamesWithTheFiltersAsQueryParameters() = runBlocking {
        val all = client().use { it.games() }
        val filtered = client().use { it.games(GameStatus.ACTIVE, 5) }
        assertEquals(
            listOf("http://api.test:8080/api/admin/games", "http://api.test:8080/api/admin/games?status=ACTIVE&limit=5"),
            api.requests.map { it.url },
        )
        assertTrue(api.requests.all { it.method == HttpMethod.Get && it.token == FakeAdminApi.TOKEN && it.body == "" })
        assertEquals(listOf("cX5ms1dZ9NoP", "aZ3kq9bX7LmN", "bY4lr0cY8MnO"), all.value.games.map { it.id })
        assertNull(all.value.games[0].black)
        assertEquals(listOf("aZ3kq9bX7LmN"), filtered.value.games.map { it.id })
    }

    @Test
    fun dropDeletesTheGameAndIgnoresTheEmptyAnswer() = runBlocking {
        val reply = client().use { it.dropGame("aZ3kq9bX7LmN") }
        val request = api.requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("http://api.test:8080/api/admin/games/aZ3kq9bX7LmN", request.url)
        assertEquals(FakeAdminApi.TOKEN, request.token)
        assertEquals("", reply.raw)
        assertEquals(Unit, reply.value)
        val error = assertFailsWith<AdminApiException> { client().use { it.dropGame("nothing") } }
        assertEquals("404 not_found: Unknown game 'nothing'", error.message)
    }

    @Test
    fun abortPostsAndReturnsTheFinishedGame() = runBlocking {
        val reply = client().use { it.abortGame("aZ3kq9bX7LmN") }
        val request = api.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("http://api.test:8080/api/admin/games/aZ3kq9bX7LmN/abort", request.url)
        assertEquals("", request.body)
        assertEquals("FINISHED", reply.value.status)
        assertEquals("ABANDONMENT", reply.value.result?.reason)
        assertNull(reply.value.result?.winner)
    }

    @Test
    fun setSettingPutsTheValueAsAString() = runBlocking {
        val reply = client().use { it.setSetting("bot.open_games_target", "3") }
        val request = api.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("http://api.test:8080/api/admin/settings/bot.open_games_target", request.url)
        assertEquals(FakeAdminApi.TOKEN, request.token)
        assertEquals(ContentType.Application.Json.toString(), request.contentType)
        assertEquals("""{"value":"3"}""", request.body)
        assertEquals("3", reply.value.value)
        assertEquals("2", reply.value.defaultValue)
        assertEquals("db", reply.value.source)
    }

    @Test
    fun resetDeletesTheSetting() = runBlocking {
        val reply = client().use { it.resetSetting("bot.open_games_target") }
        assertEquals(HttpMethod.Delete, api.requests.single().method)
        assertEquals("http://api.test:8080/api/admin/settings/bot.open_games_target", api.requests.single().url)
        assertEquals("default", reply.value.source)
    }

    @Test
    fun readsTheOverviewAndTheSettingsList() = runBlocking {
        val (overview, settings) = client().use { it.overview() to it.settings() }
        assertEquals(listOf("/api/admin/overview", "/api/admin/settings"), api.requests.map { it.url.removePrefix("http://api.test:8080") })
        assertEquals(123, overview.value.users)
        assertEquals(5, overview.value.games.active)
        assertEquals(setOf("bot.open_games_target", "bot.matchmaking"), overview.value.settings.keys)
        assertEquals(listOf("bot.matchmaking", "bot.open_games_target"), settings.value.settings.map { it.key })
    }

    @Test
    fun theServersErrorBecomesAnException() = runBlocking {
        val error = assertFailsWith<AdminApiException> { client().use { it.setSetting("bot.unknown", "1") } }
        assertEquals(400, error.status)
        assertEquals("validation", error.code)
        assertEquals("Unknown setting 'bot.unknown'", error.detail)
        assertEquals("400 validation: Unknown setting 'bot.unknown'", error.message)
    }

    @Test
    fun aWrongTokenIsUnauthorized() = runBlocking {
        val error = assertFailsWith<AdminApiException> { AdminClient(api.engine(), "http://api.test:8080", "wrong").use { it.bots() } }
        assertEquals("401 unauthorized: Bad admin token", error.message)
    }

    @Test
    fun anAnswerWithoutAnErrorBodyUsesTheReasonPhrase() = runBlocking {
        val engine = MockEngine { respond("<html>upstream down</html>", HttpStatusCode.BadGateway, headersOf(HttpHeaders.ContentType, "text/html")) }
        val error = assertFailsWith<AdminApiException> { AdminClient(engine, "http://api.test:8080", "t").use { it.overview() } }
        assertEquals("502 Bad Gateway: <html>upstream down</html>", error.message)
        val empty = MockEngine { respond("", HttpStatusCode.NotFound) }
        val notFound = assertFailsWith<AdminApiException> { AdminClient(empty, "http://api.test:8080", "t").use { it.overview() } }
        assertEquals("404 Not Found: (empty body)", notFound.message)
    }
}
