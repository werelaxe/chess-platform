package ru.werelaxe.chess.admin

import com.github.ajalt.clikt.testing.CliktCommandTestResult
import com.github.ajalt.clikt.testing.test
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import java.net.ConnectException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The command line as a user types it, through clikt's test harness, against the fake admin API. */
class CommandTest {
    private val api = FakeAdminApi()
    private val token = "--token ${FakeAdminApi.TOKEN}"

    private fun run(args: String, env: Map<String, String> = emptyMap()): CliktCommandTestResult =
        ChessAdmin { api.engine() }.test(args, envvars = env)

    private fun CliktCommandTestResult.assertFailed(fragment: String) {
        assertEquals(1, statusCode, output)
        assertTrue(fragment in stderr, stderr)
        assertEquals("", stdout)
    }

    @Test
    fun helpNeedsNoToken() {
        val result = run("--help")
        assertEquals(0, result.statusCode, result.output)
        assertTrue("Usage: chess-admin [<options>] <command> [<args>]..." in result.stdout, result.stdout)
        assertTrue(CHESS_ADMIN_TOKEN_MENTION in result.stdout, result.stdout)
        for (command in listOf("overview", "bots", "settings")) assertTrue(command in result.stdout, command)
        val spawn = run("bots spawn --help")
        assertEquals(0, spawn.statusCode, spawn.output)
        assertTrue("--level" in spawn.stdout && "NOVICE|EASY|MEDIUM|HARD|EXPERT" in spawn.stdout, spawn.stdout)
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun aMissingTokenIsAClearError() {
        run("bots list").assertFailed("Missing admin token: pass --token or set CHESS_ADMIN_TOKEN")
        run("--token '  ' bots list").assertFailed("Missing admin token")
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun theTokenAndTheUrlComeFromTheEnvironment() {
        val result = run("bots list", env = mapOf(TOKEN_ENV to FakeAdminApi.TOKEN, API_ENV to "http://api.test:9000/"))
        assertEquals(0, result.statusCode, result.output)
        val request = api.requests.single()
        assertEquals("http://api.test:9000/api/admin/bots", request.url)
        assertEquals(FakeAdminApi.TOKEN, request.token)
    }

    @Test
    fun optionsOverrideTheEnvironmentAndTheDefaultUrlIsLocalhost() {
        run("--api http://other:1 --token wrong bots list", env = mapOf(TOKEN_ENV to FakeAdminApi.TOKEN, API_ENV to "http://api.test:9000"))
            .assertFailed("Error: 401 unauthorized: Bad admin token")
        assertEquals("http://other:1/api/admin/bots", api.requests.single().url)
        assertEquals("wrong", api.requests.single().token)
        api.requests.clear()
        run("$token overview")
        assertEquals("http://localhost:8080/api/admin/overview", api.requests.single().url)
    }

    @Test
    fun overviewIsReadable() {
        val result = run("$token overview")
        assertEquals(0, result.statusCode, result.output)
        assertTrue("Users:             123" in result.stdout, result.stdout)
        assertTrue("bot.open_games_target  2" in result.stdout, result.stdout)
    }

    @Test
    fun botsListIsATable() {
        val result = run("$token bots list")
        assertEquals(0, result.statusCode, result.output)
        assertEquals(
            """
            USERNAME     NAME             LEVEL   CLASSIC  QUANTUM  ACTIVE  GAMES  LEGACY
            novice-nick  Nick the Novice  NOVICE  1480     -        1       25
            computer     Computer         MEDIUM  -        -        0       100    yes

            """.trimIndent(),
            result.stdout,
        )
        assertEquals(HttpMethod.Get, api.requests.single().method)
    }

    @Test
    fun jsonPrintsTheBodyAsTheServerSentIt() {
        val result = run("$token --json bots list")
        assertEquals(0, result.statusCode, result.output)
        assertEquals(Fixtures.BOTS + "\n", result.stdout)
        val show = run("--json $token bots show novice-nick")
        assertEquals(Fixtures.NICK_DETAILS + "\n", show.stdout)
    }

    @Test
    fun botsShowHasTheDetailsAndTheGames() {
        val result = run("$token bots show novice-nick")
        assertEquals(0, result.statusCode, result.output)
        assertEquals("http://localhost:8080/api/admin/bots/novice-nick", api.requests.single().url)
        assertTrue("Username:      novice-nick" in result.stdout, result.stdout)
        assertTrue("Active games (1)" in result.stdout && "Recent games (2)" in result.stdout, result.stdout)
        assertTrue("bY4lr0cY8MnO  CLASSIC  FINISHED  alice        novice-nick  41     no     0-1 CHECKMATE" in result.stdout, result.stdout)
    }

    @Test
    fun anApiErrorIsShownWithStatusAndCode() {
        run("$token bots show nobody").assertFailed("Error: 404 not_found: Unknown bot 'nobody'")
        run("$token settings set bot.unknown 1").assertFailed("Error: 400 validation: Unknown setting 'bot.unknown'")
    }

    @Test
    fun spawnPostsTheBotAndPrintsIt() {
        val result = run("$token bots spawn --username hard-harry --name \"Harry the Hard\" --level hard")
        assertEquals(0, result.statusCode, result.output)
        val request = api.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("http://localhost:8080/api/admin/bots", request.url)
        assertEquals("""{"username":"hard-harry","displayName":"Harry the Hard","level":"HARD"}""", request.body)
        assertEquals(
            """
            USERNAME    NAME            LEVEL  CLASSIC  QUANTUM  ACTIVE  GAMES  LEGACY
            hard-harry  Harry the Hard  HARD   -        -        0       0

            """.trimIndent(),
            result.stdout,
        )
    }

    @Test
    fun spawnChecksItsArguments() {
        run("$token bots spawn --username x --name y --level ULTRA").assertFailed("invalid choice: ULTRA")
        run("$token bots spawn --username x --level EASY").assertFailed("missing option --name")
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun setPatchesOnlyWhatWasGiven() {
        val result = run("$token bots set novice-nick --level EASY")
        assertEquals(0, result.statusCode, result.output)
        assertEquals(HttpMethod.Patch, api.requests.single().method)
        assertEquals("http://localhost:8080/api/admin/bots/novice-nick", api.requests.single().url)
        assertEquals("""{"level":"EASY"}""", api.requests.single().body)
        assertTrue("novice-nick  Nick the Novice  EASY" in result.stdout, result.stdout)
        api.requests.clear()
        run("$token bots set novice-nick --name Nick --level MEDIUM")
        assertEquals("""{"level":"MEDIUM","displayName":"Nick"}""", api.requests.single().body)
    }

    @Test
    fun setNeedsSomethingToChange() {
        run("$token bots set novice-nick").assertFailed("Nothing to change: pass --level and/or --name")
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun settingsListIsATable() {
        val result = run("$token settings list")
        assertEquals(0, result.statusCode, result.output)
        assertTrue(result.stdout.startsWith("KEY                    VALUE  DEFAULT  SOURCE   DESCRIPTION\n"), result.stdout)
        assertTrue("bot.open_games_target  3      2        db       public open games kept waiting by bots" in result.stdout, result.stdout)
    }

    @Test
    fun settingsSetAndResetTalkToTheKey() {
        val set = run("$token settings set bot.matchmaking false")
        assertEquals(0, set.statusCode, set.output)
        assertEquals(HttpMethod.Put, api.requests.single().method)
        assertEquals("http://localhost:8080/api/admin/settings/bot.matchmaking", api.requests.single().url)
        assertEquals("""{"value":"false"}""", api.requests.single().body)
        assertTrue("bot.matchmaking  false  true     db" in set.stdout, set.stdout)
        api.requests.clear()
        val reset = run("$token settings reset bot.matchmaking")
        assertEquals(0, reset.statusCode, reset.output)
        assertEquals(HttpMethod.Delete, api.requests.single().method)
        assertEquals("http://localhost:8080/api/admin/settings/bot.matchmaking", api.requests.single().url)
        assertTrue("bot.matchmaking  true   true     default" in reset.stdout, reset.stdout)
    }

    @Test
    fun openGamesIsAShortcutForTheTarget() {
        val result = run("$token settings open-games 3")
        assertEquals(0, result.statusCode, result.output)
        assertEquals(HttpMethod.Put, api.requests.single().method)
        assertEquals("http://localhost:8080/api/admin/settings/bot.open_games_target", api.requests.single().url)
        assertEquals("""{"value":"3"}""", api.requests.single().body)
        api.requests.clear()
        run("$token settings open-games -- -1").assertFailed("must not be negative")
        run("$token settings open-games many").assertFailed("many is not a valid integer")
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun pausesSetTheTwoKeys() {
        val result = run("$token settings pauses --reply 500 --bots 0")
        assertEquals(0, result.statusCode, result.output)
        assertEquals(
            listOf(
                "http://localhost:8080/api/admin/settings/bot.reply_pause_millis" to """{"value":"500"}""",
                "http://localhost:8080/api/admin/settings/bot.vs_bot_pause_millis" to """{"value":"0"}""",
            ),
            api.requests.map { it.url to it.body },
        )
        assertTrue(api.requests.all { it.method == HttpMethod.Put })
        assertEquals(
            """
            KEY                      VALUE  DEFAULT  SOURCE  DESCRIPTION
            bot.reply_pause_millis   500    700      db      a setting
            bot.vs_bot_pause_millis  0      0        db      a setting

            """.trimIndent(),
            result.stdout,
        )
        api.requests.clear()
        run("$token settings pauses --bots 250")
        assertEquals(listOf("http://localhost:8080/api/admin/settings/bot.vs_bot_pause_millis"), api.requests.map { it.url })
        api.requests.clear()
        val json = run("$token --json settings pauses --reply 1 --bots 2")
        assertEquals(2, json.stdout.trimEnd().lines().size, json.stdout)
        assertTrue(json.stdout.lines()[0].startsWith("""{"key":"bot.reply_pause_millis""""), json.stdout)
    }

    @Test
    fun pausesNeedAtLeastOnePause() {
        run("$token settings pauses").assertFailed("Nothing to change: pass --reply and/or --bots")
        run("$token settings pauses --reply -5").assertFailed("must not be negative")
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun anUnreachableServerIsReported() {
        val result = ChessAdmin { MockEngine { throw ConnectException("Connection refused") } }
            .test("--api http://nowhere:1 --token t overview")
        result.assertFailed("Error: cannot reach http://nowhere:1: Connection refused")
    }

    @Test
    fun anUnexpectedBodyIsReported() {
        val result = ChessAdmin { MockEngine { respond("<html>not json</html>") } }
            .test("$token bots list")
        result.assertFailed("Error: unexpected response from http://localhost:8080")
        val json = ChessAdmin { MockEngine { respond("<html>not json</html>") } }
            .test("$token --json bots list")
        assertEquals(0, json.statusCode, json.output)
        assertEquals("<html>not json</html>\n", json.stdout)
    }

    @Test
    fun aGroupWithoutASubcommandShowsItsHelp() {
        val result = run("$token bots")
        assertTrue("Usage: chess-admin bots [<options>] <command> [<args>]..." in result.output, result.output)
        assertTrue("spawn  Create a bot" in result.output, result.output)
        assertTrue(api.requests.isEmpty())
    }

    private companion object {
        const val CHESS_ADMIN_TOKEN_MENTION = "env CHESS_ADMIN_TOKEN"
    }
}
