package ru.werelaxe.chess.server

import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import ru.werelaxe.chess.core.ChessJson
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.engine.ThinkPriority
import ru.werelaxe.chess.server.dto.AdminBot
import ru.werelaxe.chess.server.dto.AdminBotDetails
import ru.werelaxe.chess.server.dto.AdminBotsResponse
import ru.werelaxe.chess.server.dto.AdminOverview
import ru.werelaxe.chess.server.dto.BotsResponse
import ru.werelaxe.chess.server.dto.ColorChoice
import ru.werelaxe.chess.server.dto.GameCounts
import ru.werelaxe.chess.server.dto.GameDto
import ru.werelaxe.chess.server.dto.GameSummary
import ru.werelaxe.chess.server.dto.GamesResponse
import ru.werelaxe.chess.server.dto.PlayerRatings
import ru.werelaxe.chess.server.dto.SettingEntry
import ru.werelaxe.chess.server.dto.SettingSource
import ru.werelaxe.chess.server.dto.SettingsResponse
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.model.GameResult
import ru.werelaxe.chess.server.model.Visibility
import ru.werelaxe.chess.server.plugins.ADMIN_TOKEN_HEADER
import ru.werelaxe.chess.server.repository.memory.InMemoryStore
import ru.werelaxe.chess.server.service.BotMatchmaker.Action
import ru.werelaxe.chess.server.service.BotRoster
import ru.werelaxe.chess.server.service.MoveProvider
import ru.werelaxe.chess.server.service.SettingKeys
import ru.werelaxe.chess.server.ws.ServerEvent
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private const val ADMIN_TOKEN = "admin-token-for-tests-0123"

/** The admin API switched on; everything else as in the other tests. */
private val ADMIN_CONFIG = TEST_CONFIG.copy(adminToken = ADMIN_TOKEN)

class AdminTest {
    private val roster = BotRoster.specs.filter { !it.legacy }.map { it.username }

    private fun HttpRequestBuilder.admin(token: String?) {
        if (token != null) header(ADMIN_TOKEN_HEADER, token)
    }

    private suspend fun TestContext.adminGet(path: String, token: String? = ADMIN_TOKEN): HttpResponse =
        client.get("/api/admin$path") { admin(token) }

    private suspend fun TestContext.adminPost(path: String, token: String? = ADMIN_TOKEN): HttpResponse =
        client.post("/api/admin$path") { admin(token) }

    private suspend fun TestContext.adminDelete(path: String, token: String? = ADMIN_TOKEN): HttpResponse =
        client.delete("/api/admin$path") { admin(token) }

    /** `GET /api/admin/games` with the given query string. */
    private suspend fun TestContext.adminGames(query: String = ""): List<GameSummary> {
        val response = adminGet("/games$query")
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsString())
        return response.body<GamesResponse>().games
    }

    private suspend fun ReceiveChannel<Frame>.nextEvent(): ServerEvent = withTimeout(5_000) {
        val frame = assertIs<Frame.Text>(receive())
        ChessJson.json.decodeFromString(ServerEvent.serializer(), frame.readText())
    }

    /** A move provider that never moves, for the tests that only count the bots' games. */
    private val idleBots = object : MoveProvider {
        override suspend fun chooseMove(
            gameId: String,
            kind: GameKind,
            level: EngineLevel,
            moves: List<GameMove>,
            priority: ThinkPriority,
        ): GameMove? = null
    }

    private suspend fun TestContext.spawn(username: String, name: String, level: String, token: String? = ADMIN_TOKEN): HttpResponse =
        client.post("/api/admin/bots") {
            admin(token)
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$username","displayName":"$name","level":"$level"}""")
        }

    private suspend fun TestContext.patchBot(username: String, body: String): HttpResponse =
        client.patch("/api/admin/bots/$username") {
            admin(ADMIN_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun TestContext.putSetting(key: String, body: String, token: String? = ADMIN_TOKEN): HttpResponse =
        client.put("/api/admin/settings/$key") {
            admin(token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    /** Sets a setting and returns the entry the server answers with. */
    private suspend fun TestContext.set(key: String, value: String): SettingEntry {
        val response = putSetting(key, """{"value":"$value"}""")
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsString())
        return response.body()
    }

    private suspend fun TestContext.resetSetting(key: String): HttpResponse =
        client.delete("/api/admin/settings/$key") { admin(ADMIN_TOKEN) }

    private suspend fun TestContext.settings(): List<SettingEntry> = adminGet("/settings").body<SettingsResponse>().settings

    private suspend fun TestContext.setting(key: String): SettingEntry = settings().single { it.key == key }

    private suspend fun TestContext.adminBots(): List<AdminBot> = adminGet("/bots").body<AdminBotsResponse>().bots

    private suspend fun TestContext.offeredBots(): List<String> =
        client.get("/api/bots").body<BotsResponse>().bots.map { it.user.username }

    private suspend fun TestContext.openGames(): List<String> =
        client.get("/api/games?filter=open").body<GamesResponse>().games.map { it.id }

    @Test
    fun adminApiIsAbsentWithoutAConfiguredToken() = serverTest {
        adminGet("/overview").assertError(HttpStatusCode.NotFound, "not_found")
        adminGet("/bots").assertError(HttpStatusCode.NotFound, "not_found")
        adminGet("/settings").assertError(HttpStatusCode.NotFound, "not_found")
        adminGet("/games").assertError(HttpStatusCode.NotFound, "not_found")
        spawn("fresh_fred", "Fresh Fred", "EASY").assertError(HttpStatusCode.NotFound, "not_found")
        putSetting("bot.open_games_target", """{"value":"3"}""").assertError(HttpStatusCode.NotFound, "not_found")
        assertEquals(roster, offeredBots(), "nothing was created")
    }

    @Test
    fun adminApiRequiresTheConfiguredToken() = serverTest(config = ADMIN_CONFIG) {
        adminGet("/overview", token = null).assertError(HttpStatusCode.Unauthorized, "unauthorized")
        adminGet("/overview", token = "wrong").assertError(HttpStatusCode.Unauthorized, "unauthorized")
        adminGet("/overview", token = "$ADMIN_TOKEN-almost").assertError(HttpStatusCode.Unauthorized, "unauthorized")
        adminGet("/overview", token = ADMIN_TOKEN.dropLast(1)).assertError(HttpStatusCode.Unauthorized, "unauthorized")
        spawn("fresh_fred", "Fresh Fred", "EASY", token = "wrong").assertError(HttpStatusCode.Unauthorized, "unauthorized")
        putSetting("bot.open_games_target", """{"value":"3"}""", token = null).assertError(HttpStatusCode.Unauthorized, "unauthorized")
        adminGet("/games", token = null).assertError(HttpStatusCode.Unauthorized, "unauthorized")
        adminDelete("/games/missing12345", token = "wrong").assertError(HttpStatusCode.Unauthorized, "unauthorized")
        adminPost("/games/missing12345/abort", token = null).assertError(HttpStatusCode.Unauthorized, "unauthorized")
        assertEquals(roster, offeredBots(), "a rejected request changes nothing")
        assertEquals("2", setting("bot.open_games_target").value)

        assertEquals(HttpStatusCode.OK, adminGet("/overview").status)
        adminGet("/nothing").assertError(HttpStatusCode.NotFound, "not_found")
        // The public API is not guarded.
        assertEquals(HttpStatusCode.OK, client.get("/api/bots").status)
    }

    @Test
    fun overviewCountsAccountsGamesAndSettings() = serverTest(config = ADMIN_CONFIG) {
        val alice = token("alice")
        val bob = token("bob")
        guest()
        createGame(alice)
        activeGame(alice, bob)
        foolsMate(alice, bob, activeGame(alice, bob).id)
        botGame(alice, color = ColorChoice.WHITE)

        val overview = adminGet("/overview").body<AdminOverview>()
        assertEquals(2, overview.users)
        assertEquals(1, overview.guests)
        assertEquals(BotRoster.specs.size, overview.bots, "the legacy account is a bot too")
        assertEquals(GameCounts(waiting = 1, active = 2, finished = 1), overview.games)
        assertEquals(1, overview.activeBotGames)
        assertEquals(2, overview.humanGames, "the game against the bot and the game between the two people")
        assertEquals(SettingKeys.ALL.map { it.name }.toSet(), overview.settings.keys)
        assertEquals("2", overview.settings["bot.open_games_target"])
        assertEquals("0", overview.settings["bot.live_games_target"])
        assertEquals("true", overview.settings["bot.matchmaking"])
    }

    @Test
    fun botsAreListedAndShownWithRatingsAndGames() = serverTest(config = ADMIN_CONFIG) {
        val alice = token("alice")
        val easy = botNamed(EngineLevel.EASY)
        // One finished rated game against the easy bot, which it won, and one in progress.
        val finished = invite(alice, easy, color = ColorChoice.WHITE)
        move(alice, finished.id, "e2e4")
        awaitMoves(alice, finished.id, 2)
        assertEquals(HttpStatusCode.OK, resign(alice, finished.id).status)
        val active = invite(alice, easy, color = ColorChoice.BLACK)
        awaitMoves(alice, active.id, 1)

        val listed = adminBots()
        assertEquals(BotRoster.specs.map { it.username }, listed.map { it.user.username }, "roster order, the legacy account last")
        val bot = listed.single { it.user.username == easy }
        assertEquals(EngineLevel.EASY, bot.level)
        assertEquals(EngineLevel.EASY, bot.user.botLevel)
        assertTrue(bot.user.bot)
        assertFalse(bot.legacy)
        assertEquals(1, bot.activeGames)
        assertEquals(2, bot.totalGames)
        assertEquals(1520, bot.ratings.classic?.rating)
        val computer = listed.single { it.user.username == BotRoster.COMPUTER }
        assertTrue(computer.legacy)
        assertEquals(0, computer.totalGames)
        assertEquals(PlayerRatings.NONE, computer.ratings)
        val json = adminGet("/bots").bodyAsString()
        assertTrue("\"legacy\":false" in json, json)

        val details = adminGet("/bots/$easy").body<AdminBotDetails>()
        assertEquals(bot.user, details.user)
        assertEquals(bot.ratings, details.ratings)
        assertEquals(1, details.activeGames)
        assertEquals(2, details.totalGames)
        assertEquals(listOf(active.id), details.activeGameList.map { it.id })
        assertEquals(GamePhase.ACTIVE, details.activeGameList.single().status)
        assertEquals(setOf(active.id, finished.id), details.recentGames.map { it.id }.toSet())
        assertEquals(details.user, adminGet("/bots/${easy.uppercase()}").body<AdminBotDetails>().user, "looked up like any username")

        adminGet("/bots/alice").assertError(HttpStatusCode.NotFound, "not_found")
        adminGet("/bots/nobody").assertError(HttpStatusCode.NotFound, "not_found")
    }

    @Test
    fun spawnValidatesTheRequestAndRejectsTakenNames() = serverTest(config = ADMIN_CONFIG) {
        token("alice")
        spawn("ab", "Ab", "EASY").assertError(HttpStatusCode.BadRequest, "validation")
        spawn("bad name", "Bad", "EASY").assertError(HttpStatusCode.BadRequest, "validation")
        spawn("guest-123456", "Guest", "EASY").assertError(HttpStatusCode.BadRequest, "validation")
        spawn("bots", "Bots", "EASY").assertError(HttpStatusCode.BadRequest, "validation")
        spawn("fresh_fred", "   ", "EASY").assertError(HttpStatusCode.BadRequest, "validation")
        spawn("fresh_fred", "Fred", "GODLIKE").assertError(HttpStatusCode.BadRequest, "validation")
        client.post("/api/admin/bots") {
            admin(ADMIN_TOKEN)
            contentType(ContentType.Application.Json)
            setBody("""{"username":"fresh_fred"}""")
        }.assertError(HttpStatusCode.BadRequest, "validation")
        spawn("alice", "Alice", "EASY").assertError(HttpStatusCode.Conflict, "username_taken")
        spawn(botNamed(EngineLevel.EASY).uppercase(), "Pawn", "EASY").assertError(HttpStatusCode.Conflict, "username_taken")
        spawn(BotRoster.COMPUTER, "Computer", "EASY").assertError(HttpStatusCode.Conflict, "username_taken")
        assertEquals(roster, offeredBots())
        assertEquals(BotRoster.specs.size, adminBots().size)
    }

    @Test
    fun spawnedBotJoinsTheRosterAndPlays() = serverTest(config = ADMIN_CONFIG.copy(botMaxActiveGames = 1), random = ScriptedRandom()) {
        val alice = token("alice")
        // Every roster bot is busy, so the matchmaker has nobody to act with.
        for (bot in roster) invite(alice, bot, color = ColorChoice.WHITE)
        assertEquals(emptyList(), services.matchmaker.tick())

        val response = spawn("fresh_fred", "  Fresh Fred  ", "HARD")
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsString())
        val created = response.body<AdminBot>()
        assertEquals("fresh_fred", created.user.username)
        assertEquals("Fresh Fred", created.user.name)
        assertTrue(created.user.bot)
        assertEquals(EngineLevel.HARD, created.level)
        assertEquals(EngineLevel.HARD, created.user.botLevel)
        assertFalse(created.legacy)
        assertEquals(0, created.activeGames)
        assertEquals(0, created.totalGames)
        assertEquals(PlayerRatings.NONE, created.ratings)
        spawn("Fresh_Fred", "Twin", "EASY").assertError(HttpStatusCode.Conflict, "username_taken")

        // Offered to players right away, after the code-defined roster and before the legacy account.
        assertEquals(roster + "fresh_fred", offeredBots())
        assertEquals(roster + "fresh_fred" + BotRoster.COMPUTER, adminBots().map { it.user.username })
        assertEquals("Fresh Fred", profile("fresh_fred").user.name)

        // The matchmaker sees it without a restart: it is the only bot with a free slot.
        val tick = assertIs<Action.Created>(services.matchmaker.tick().single())
        assertEquals("fresh_fred", game(tick.gameId).creator.username)

        // And it plays when invited, like a roster bot.
        val bob = token("bob")
        val game = invite(bob, "fresh_fred", color = ColorChoice.WHITE)
        assertEquals(GamePhase.ACTIVE, game.status)
        assertEquals(EngineLevel.HARD, game.black?.botLevel)
        move(bob, game.id, "e2e4")
        assertEquals(2, awaitMoves(bob, game.id, 2).moveCount)
        val busy = adminGet("/bots/fresh_fred").body<AdminBotDetails>()
        assertEquals(1, busy.activeGames)
        assertEquals(2, busy.totalGames)

        // Like the roster's bots, it cannot be logged into or registered.
        login("fresh_fred", TEST_PASSWORD).assertError(HttpStatusCode.Unauthorized, "invalid_credentials")
        register("fresh_fred").assertError(HttpStatusCode.Conflict, "username_taken")
    }

    @Test
    fun rosterSeedingNeverOverridesAChangedBot() {
        val repositories = InMemoryStore().repositories()
        val easy = botNamed(EngineLevel.EASY)
        runBlocking { assertNotNull(repositories.users.createBot(easy, "Custom Name", EngineLevel.HARD, TEST_START)) }
        serverTest(repositories = repositories) {
            val bots = client.get("/api/bots").body<BotsResponse>().bots
            assertEquals(roster, bots.map { it.user.username }, "the missing bots were created")
            val kept = bots.single { it.user.username == easy }
            assertEquals(EngineLevel.HARD, kept.user.botLevel)
            assertEquals("Custom Name", kept.user.name)
        }
    }

    @Test
    fun patchChangesLevelAndDisplayName() = serverTest(config = ADMIN_CONFIG) {
        val easy = botNamed(EngineLevel.EASY)
        patchBot(easy, "{}").assertError(HttpStatusCode.BadRequest, "validation")
        patchBot(easy, """{"level":"GODLIKE"}""").assertError(HttpStatusCode.BadRequest, "validation")
        patchBot(easy, """{"displayName":"   "}""").assertError(HttpStatusCode.BadRequest, "validation")
        patchBot(easy, """{"displayName":"${"x".repeat(41)}"}""").assertError(HttpStatusCode.BadRequest, "validation")
        patchBot("nobody", """{"level":"HARD"}""").assertError(HttpStatusCode.NotFound, "not_found")
        val alice = token("alice")
        patchBot("alice", """{"level":"HARD"}""").assertError(HttpStatusCode.NotFound, "not_found")

        val response = patchBot(easy, """{"level":"HARD","displayName":"  Pawn Pro  "}""")
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsString())
        val changed = response.body<AdminBot>()
        assertEquals(EngineLevel.HARD, changed.level)
        assertEquals("Pawn Pro", changed.user.name)

        // Visible at once: in the public roster, on the profile and in new games.
        val offered = client.get("/api/bots").body<BotsResponse>().bots.single { it.user.username == easy }
        assertEquals(EngineLevel.HARD, offered.user.botLevel)
        assertEquals("Pawn Pro", offered.user.name)
        assertEquals("Pawn Pro", profile(easy).user.name)
        val game = invite(alice, easy, color = ColorChoice.WHITE)
        assertEquals(EngineLevel.HARD, game.black?.botLevel)
        assertEquals("Pawn Pro", game.black?.name)

        // One field at a time: the other stays.
        val again = patchBot(easy, """{"level":"NOVICE"}""").body<AdminBot>()
        assertEquals(EngineLevel.NOVICE, again.level)
        assertEquals("Pawn Pro", again.user.name)
        val renamed = patchBot(easy, """{"displayName":"Pawn Pal"}""").body<AdminBot>()
        assertEquals(EngineLevel.NOVICE, renamed.level)
        assertEquals("Pawn Pal", renamed.user.name)
        // The legacy account can be changed too.
        assertEquals("Old Computer", patchBot(BotRoster.COMPUTER, """{"displayName":"Old Computer"}""").body<AdminBot>().user.name)
    }

    @Test
    fun settingsAreListedChangedAndReset() = serverTest(config = ADMIN_CONFIG.copy(botOpenGamesTarget = 3)) {
        val listed = settings()
        assertEquals(SettingKeys.ALL.map { it.name }, listed.map { it.key })
        assertTrue(listed.all { it.source == SettingSource.DEFAULT && it.value == it.defaultValue && it.description.isNotBlank() })
        assertEquals("3", setting("bot.open_games_target").value, "the default comes from the configuration")
        assertEquals(
            listOf("bot.matchmaking", "bot.open_games_target", "bot.live_games_target", "bot.human_games_limit"),
            listed.take(4).map { it.key },
        )
        assertEquals("0", setting("bot.live_games_target").value)
        assertEquals("Bot-versus-bot games the matchmaker keeps in progress; 0 switches this off", setting("bot.live_games_target").description)
        assertEquals("0", setting("bot.human_games_limit").value)
        assertEquals(
            "Games in progress with a human at or above which bots start no games with each other and bot-versus-bot games wait; 0 switches this off",
            setting("bot.human_games_limit").description,
        )
        assertEquals("true", setting("bot.matchmaking").value)
        assertEquals("10", setting("bot.reply_pause_millis").value)
        assertEquals("0", setting("bot.vs_bot_pause_millis").value)
        assertEquals("6", setting("bot.max_active_games").value)
        assertEquals("8", setting("bot.join_after_minutes").value)
        assertEquals("60", setting("bot.invitation_ttl_minutes").value)
        assertEquals("3", setting("bot.tick_min_minutes").value)
        assertEquals("10", setting("bot.tick_max_minutes").value)
        val json = adminGet("/settings").bodyAsString()
        assertTrue("\"source\":\"default\"" in json, json)
        assertTrue("\"default\":\"3\"" in json, json)

        val changed = set("bot.open_games_target", " 5 ")
        assertEquals("bot.open_games_target", changed.key)
        assertEquals("5", changed.value, "stored trimmed")
        assertEquals("3", changed.defaultValue)
        assertEquals(SettingSource.DB, changed.source)
        assertEquals(changed, setting("bot.open_games_target"))
        assertEquals(5, services.settings.openGamesTarget)
        assertEquals("5", adminGet("/overview").body<AdminOverview>().settings["bot.open_games_target"])
        assertEquals("true", set("bot.matchmaking", "TRUE").value, "booleans are normalised")
        assertEquals("40", set("bot.human_games_limit", "40").value)
        assertEquals(40, services.settings.humanGamesLimit)
        assertEquals("250", putSetting("bot.reply_pause_millis", """{"value":250}""").body<SettingEntry>().value, "a bare number is accepted")

        putSetting("bot.open_games_target", """{"value":"-1"}""").assertError(HttpStatusCode.BadRequest, "validation")
        putSetting("bot.live_games_target", """{"value":"-1"}""").assertError(HttpStatusCode.BadRequest, "validation")
        putSetting("bot.human_games_limit", """{"value":"-1"}""").assertError(HttpStatusCode.BadRequest, "validation")
        putSetting("bot.open_games_target", """{"value":"many"}""").assertError(HttpStatusCode.BadRequest, "validation")
        putSetting("bot.open_games_target", """{"value":"1.5"}""").assertError(HttpStatusCode.BadRequest, "validation")
        putSetting("bot.open_games_target", """{"value":null}""").assertError(HttpStatusCode.BadRequest, "validation")
        putSetting("bot.open_games_target", """{}""").assertError(HttpStatusCode.BadRequest, "validation")
        putSetting("bot.max_active_games", """{"value":"0"}""").assertError(HttpStatusCode.BadRequest, "validation")
        putSetting("bot.invitation_ttl_minutes", """{"value":"0"}""").assertError(HttpStatusCode.BadRequest, "validation")
        putSetting("bot.tick_min_minutes", """{"value":"0"}""").assertError(HttpStatusCode.BadRequest, "validation")
        putSetting("bot.matchmaking", """{"value":"yes"}""").assertError(HttpStatusCode.BadRequest, "validation")
        putSetting("bot.unknown", """{"value":"1"}""").assertError(HttpStatusCode.BadRequest, "validation")
        putSetting("BOT.OPEN_GAMES_TARGET", """{"value":"1"}""").assertError(HttpStatusCode.BadRequest, "validation")
        resetSetting("bot.unknown").assertError(HttpStatusCode.BadRequest, "validation")
        assertEquals("5", setting("bot.open_games_target").value, "a rejected value changes nothing")
        assertEquals(5, services.settings.openGamesTarget)

        // The upper bound of the tick range is raised to the lower one when set below it.
        set("bot.tick_min_minutes", "5")
        assertEquals("2", set("bot.tick_max_minutes", "2").value, "stored as given")
        assertEquals(Duration.ofMinutes(5), services.settings.tickMin)
        assertEquals(Duration.ofMinutes(5), services.settings.tickMax)

        val reset = resetSetting("bot.open_games_target")
        assertEquals(HttpStatusCode.OK, reset.status, reset.bodyAsString())
        val entry = reset.body<SettingEntry>()
        assertEquals("3", entry.value)
        assertEquals("3", entry.defaultValue)
        assertEquals(SettingSource.DEFAULT, entry.source)
        assertEquals(entry, setting("bot.open_games_target"))
        assertEquals(3, services.settings.openGamesTarget)
        assertEquals(HttpStatusCode.OK, resetSetting("bot.open_games_target").status, "resetting a default is harmless")
        assertEquals(SettingSource.DEFAULT, setting("bot.open_games_target").source)
    }

    @Test
    fun openGamesTargetChangeIsAppliedByTheNextTick() = serverTest(config = ADMIN_CONFIG, random = ScriptedRandom()) {
        assertIs<Action.Created>(services.matchmaker.tick().single())
        assertIs<Action.Created>(services.matchmaker.tick().single())
        assertEquals(emptyList(), services.matchmaker.tick(), "the configured target of 2 is reached")

        set("bot.open_games_target", "3")
        assertIs<Action.Created>(services.matchmaker.tick().single())
        assertEquals(emptyList(), services.matchmaker.tick())
        assertEquals(3, openGames().size)

        set("bot.open_games_target", "0")
        assertEquals(emptyList(), services.matchmaker.tick())
        assertEquals(3, openGames().size, "existing games are left alone")

        assertEquals(HttpStatusCode.OK, resetSetting("bot.open_games_target").status)
        assertEquals(emptyList(), services.matchmaker.tick(), "back to 2, which is exceeded")
        assertEquals(3, openGames().size)
    }

    @Test
    fun liveGamesTargetChangeIsAppliedByTheNextTick() = serverTest(
        config = ADMIN_CONFIG.copy(botOpenGamesTarget = 0),
        random = ScriptedRandom(),
        moveProvider = idleBots,
    ) {
        assertEquals("0", setting("bot.live_games_target").value, "the default comes from the configuration")
        assertEquals(emptyList(), services.matchmaker.tick())

        set("bot.live_games_target", "1")
        assertEquals(1, services.settings.liveGamesTarget)
        val started = assertIs<Action.Started>(services.matchmaker.tick().single())
        val live = game(started.gameId)
        assertEquals(GamePhase.ACTIVE, live.status)
        assertTrue(live.white?.bot == true && live.black?.bot == true)
        val overview = adminGet("/overview").body<AdminOverview>()
        assertEquals(1, overview.activeBotGames)
        assertEquals(0, overview.humanGames, "a game between two bots is nobody's")
        assertEquals(emptyList(), services.matchmaker.tick(), "the target of one is reached")

        set("bot.live_games_target", "0")
        assertEquals(HttpStatusCode.OK, adminPost("/games/${started.gameId}/abort").status)
        assertEquals(emptyList(), services.matchmaker.tick(), "switched off: the aborted game is not replaced")
        assertEquals(0, adminGet("/overview").body<AdminOverview>().activeBotGames)
    }

    @Test
    fun matchmakingIsPausedAndResumedWithoutARestart() = serverTest(config = ADMIN_CONFIG.copy(botMatchmaking = false), random = ScriptedRandom()) {
        assertFalse(services.settings.matchmaking)
        assertEquals(emptyList(), services.matchmaker.tick(), "off by configuration")
        assertTrue(openGames().isEmpty())

        set("bot.matchmaking", "true")
        assertTrue(services.settings.matchmaking)
        assertIs<Action.Created>(services.matchmaker.tick().single())
        assertEquals(1, openGames().size)

        set("bot.matchmaking", "false")
        assertEquals(emptyList(), services.matchmaker.tick(), "paused: the tick does nothing")
        assertEquals(1, openGames().size)

        assertEquals(HttpStatusCode.OK, resetSetting("bot.matchmaking").status)
        assertEquals(emptyList(), services.matchmaker.tick(), "the configured default is off")
        set("bot.matchmaking", "true")
        assertIs<Action.Created>(services.matchmaker.tick().single())
        assertEquals(2, openGames().size)
    }

    @Test
    fun replyPauseIsReadFromTheSettingsBeforeEveryMove() = serverTest(config = ADMIN_CONFIG) {
        val alice = token("alice")
        val created = botGame(alice, color = ColorChoice.WHITE)
        // Changed after the game started: the bot player reads the pause before every move.
        set("bot.reply_pause_millis", "400")
        val moved = TimeSource.Monotonic.markNow()
        move(alice, created.id, "e2e4")
        assertEquals(2, awaitMoves(alice, created.id, 2).moveCount)
        assertTrue(moved.elapsedNow() >= 400.milliseconds, "the reply came after ${moved.elapsedNow()}")
    }

    @Test
    fun botVersusBotPauseIsReadFromTheSettings() {
        val opening = listOf(normal("e2e4"), normal("e7e5"), normal("g1f3"), normal("b8c6"))
        val scripted = object : MoveProvider {
            override suspend fun chooseMove(
                gameId: String,
                kind: GameKind,
                level: EngineLevel,
                moves: List<GameMove>,
                priority: ThinkPriority,
            ): GameMove? = opening.getOrNull(moves.size)
        }
        serverTest(config = ADMIN_CONFIG.copy(botOpenGamesTarget = 1), random = ScriptedRandom(), moveProvider = scripted) {
            set("bot.vs_bot_pause_millis", "300")
            val created = assertIs<Action.Created>(services.matchmaker.tick().single())
            clock.advance(Duration.ofMinutes(9))
            val started = TimeSource.Monotonic.markNow()
            assertEquals(listOf(Action.Joined(created.gameId)), services.matchmaker.tick())
            // The first move follows the join at once; each of the next three waits for the pause.
            assertEquals(4, awaitStoredMoves(created.gameId, 4))
            assertTrue(started.elapsedNow() >= 900.milliseconds, "three paced moves took ${started.elapsedNow()}")
            assertEquals(opening, game(created.gameId).moves)
        }
    }

    @Test
    fun gamesAreListedNewestFirstWithAStatusFilter() = serverTest(config = ADMIN_CONFIG) {
        val alice = token("alice")
        val bob = token("bob")
        val waiting = createGame(alice)
        clock.advance(Duration.ofMinutes(1))
        val hidden = createGame(alice, visibility = Visibility.PRIVATE)
        clock.advance(Duration.ofMinutes(1))
        val invitation = invite(alice, "bob")
        clock.advance(Duration.ofMinutes(1))
        val active = activeGame(alice, bob)
        clock.advance(Duration.ofMinutes(1))
        val finished = activeGame(alice, bob)
        foolsMate(alice, bob, finished.id)

        assertEquals(
            listOf(finished.id, active.id, invitation.id, hidden.id, waiting.id),
            adminGames().map { it.id },
            "every game, newest first, whatever its visibility",
        )
        assertEquals(listOf(invitation.id, hidden.id, waiting.id), adminGames("?status=WAITING").map { it.id })
        assertEquals(listOf(active.id), adminGames("?status=active").map { it.id }, "the status is matched regardless of case")
        val over = adminGames("?status=FINISHED").single()
        assertEquals(finished.id, over.id)
        assertEquals(GamePhase.FINISHED, over.status)
        assertEquals(listOf(active.id, invitation.id), adminGames("?limit=2&offset=1").map { it.id })
        adminGet("/games?status=open").assertError(HttpStatusCode.BadRequest, "validation")
        assertEquals(listOf(waiting.id), client.get("/api/games?filter=open").body<GamesResponse>().games.map { it.id }, "the public listing is unchanged")
    }

    @Test
    fun deleteRemovesAGameWithItsMovesAndRatingChanges() = serverTest(config = ADMIN_CONFIG) {
        val alice = token("alice")
        val bob = token("bob")
        val finished = activeGame(alice, bob)
        foolsMate(alice, bob, finished.id)
        assertEquals(2, repositories.ratings.changesFor(finished.id).size)
        val bobRating = assertNotNull(profile("bob").ratings.classic).rating

        val response = adminDelete("/games/${finished.id}")
        assertEquals(HttpStatusCode.NoContent, response.status, response.bodyAsString())
        getGame(finished.id).assertError(HttpStatusCode.NotFound, "not_found")
        assertTrue(adminGames().none { it.id == finished.id })
        assertTrue(myGames(alice).none { it.id == finished.id })
        assertTrue(profile("bob").recentGames.none { it.id == finished.id })
        assertTrue(repositories.games.moves(finished.id).isEmpty())
        assertTrue(repositories.ratings.changesFor(finished.id).isEmpty())
        assertEquals(bobRating, profile("bob").ratings.classic?.rating, "the ratings the game led to stay")
        adminDelete("/games/${finished.id}").assertError(HttpStatusCode.NotFound, "not_found")
        adminPost("/games/${finished.id}/abort").assertError(HttpStatusCode.NotFound, "not_found")
        adminDelete("/games/nope").assertError(HttpStatusCode.NotFound, "not_found")

        // A game in progress goes the same way: its players simply find it gone.
        val active = activeGame(alice, bob)
        assertEquals(HttpStatusCode.NoContent, adminDelete("/games/${active.id}").status)
        moveResponse(alice, active.id, normal("e2e4")).assertError(HttpStatusCode.NotFound, "not_found")
        assertEquals(GameCounts(waiting = 0, active = 0, finished = 0), adminGet("/overview").body<AdminOverview>().games)
    }

    @Test
    fun abortFinishesAGameByAbandonmentWithoutRatingChanges() = serverTest(config = ADMIN_CONFIG) {
        val alice = token("alice")
        val bob = token("bob")
        val active = activeGame(alice, bob)
        move(alice, active.id, "e2e4")
        move(bob, active.id, "e7e5")

        client.webSocket("/api/games/${active.id}/ws") {
            assertEquals(GamePhase.ACTIVE, assertIs<ServerEvent.GameUpdated>(incoming.nextEvent()).game.status)
            val response = adminPost("/games/${active.id}/abort")
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsString())
            val aborted = response.body<GameDto>()
            assertEquals(GamePhase.FINISHED, aborted.status)
            assertEquals(GameResult(null, EndReason.ABANDONMENT), aborted.result)
            assertEquals(2, aborted.moves.size)
            assertNull(aborted.ratingChanges)
            assertEquals(aborted, assertIs<ServerEvent.GameUpdated>(incoming.nextEvent()).game, "the subscribers see the game end")
        }
        assertEquals(GamePhase.FINISHED, game(active.id).status)
        assertTrue(repositories.ratings.changesFor(active.id).isEmpty())
        assertEquals(PlayerRatings.NONE, profile("alice").ratings)
        assertEquals(PlayerRatings.NONE, profile("bob").ratings)
        moveResponse(alice, active.id, normal("g1f3")).assertError(HttpStatusCode.Conflict, "game_finished")
        adminPost("/games/${active.id}/abort").assertError(HttpStatusCode.Conflict, "game_finished")

        val finished = activeGame(alice, bob)
        foolsMate(alice, bob, finished.id)
        adminPost("/games/${finished.id}/abort").assertError(HttpStatusCode.Conflict, "game_finished")
        adminPost("/games/nope/abort").assertError(HttpStatusCode.NotFound, "not_found")

        // A waiting game is finished the same way rather than deleted, so that the record stays.
        val waiting = createGame(alice)
        val cancelled = adminPost("/games/${waiting.id}/abort").body<GameDto>()
        assertEquals(GamePhase.FINISHED, cancelled.status)
        assertEquals(GameResult(null, EndReason.ABANDONMENT), cancelled.result)
        assertEquals(GamePhase.FINISHED, game(waiting.id).status)
        joinResponse(bob, waiting.id).assertError(HttpStatusCode.Conflict, "game_full")
        assertTrue(client.get("/api/games?filter=open").body<GamesResponse>().games.isEmpty())
    }
}
