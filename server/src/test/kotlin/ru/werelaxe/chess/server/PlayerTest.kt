package ru.werelaxe.chess.server

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.server.dto.BotsResponse
import ru.werelaxe.chess.server.dto.DrawAction
import ru.werelaxe.chess.server.dto.LeaderboardResponse
import ru.werelaxe.chess.server.dto.PlayerRatings
import ru.werelaxe.chess.server.dto.PlayersResponse
import ru.werelaxe.chess.server.dto.RatingSummary
import ru.werelaxe.chess.server.model.Visibility
import ru.werelaxe.chess.server.service.BotRoster
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerTest {
    private suspend fun TestContext.search(query: String): List<String> =
        client.get("/api/players$query").body<PlayersResponse>().players.map { it.user.username }

    private suspend fun TestContext.leaderboardResponse(query: String): HttpResponse =
        client.get("/api/players/leaderboard$query")

    private suspend fun TestContext.leaderboard(query: String): LeaderboardResponse = leaderboardResponse(query).body()

    @Test
    fun searchMatchesUsernamesAndDisplayNamesButNeverGuests() = serverTest {
        val alice = token("alice")
        token("bob")
        guest()
        assertEquals(HttpStatusCode.OK, setDisplayName(alice, "Alice Wonderland").status)

        assertEquals(listOf("alice"), search("?query=won"))
        assertEquals(listOf("alice"), search("?query=ALI"))
        assertEquals(listOf("bob"), search("?query=bob"))
        assertEquals(listOf("bishop_bud", "bob"), search("?query=b"))
        assertTrue(search("?query=guest").isEmpty())
        assertTrue(search("?query=%25").isEmpty(), "LIKE wildcards are literal")

        val everybody = search("")
        assertEquals(2 + BotRoster.specs.size, everybody.size)
        assertEquals(everybody.sortedBy { it.lowercase() }, everybody)
        assertTrue(everybody.none { it.startsWith("guest-") })
        assertEquals(everybody.subList(1, 3), search("?limit=2&offset=1"))

        val summary = client.get("/api/players?query=won").body<PlayersResponse>().players.single()
        assertEquals("Alice Wonderland", summary.user.name)
        assertEquals(PlayerRatings.NONE, summary.ratings)
    }

    @Test
    fun leaderboardOrdersByRatingGamesAndUsername() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val carol = token("carol")
        val dave = token("dave")
        val frank = token("frank")
        token("erin")
        leaderboardResponse("").assertError(HttpStatusCode.BadRequest, "validation")
        leaderboardResponse("?kind=HEX").assertError(HttpStatusCode.BadRequest, "validation")
        assertTrue(leaderboard("?kind=CLASSIC").entries.isEmpty())

        foolsMate(alice, bob, activeGame(alice, bob).id)
        suspend fun agreedDraw(white: String, black: String) {
            val game = activeGame(white, black)
            move(white, game.id, "e2e4")
            move(black, game.id, "e7e5")
            draw(white, game.id, DrawAction.OFFER)
            assertEquals(HttpStatusCode.OK, draw(black, game.id, DrawAction.ACCEPT).status)
        }
        agreedDraw(carol, dave)
        agreedDraw(dave, frank)

        val entries = leaderboard("?kind=CLASSIC").entries
        assertEquals(listOf("bob", "dave", "carol", "frank", "alice"), entries.map { it.user.username })
        assertEquals(listOf(1, 2, 3, 4, 5), entries.map { it.rank })
        assertEquals(listOf(1520, 1500, 1500, 1500, 1480), entries.map { it.rating })
        assertEquals(1520, entries.first().user.rating)
        assertEquals(RatingSummary(1500, games = 2, wins = 0, draws = 2, losses = 0, peak = 1500), entries[1].ratings.classic)
        assertNull(entries[1].ratings.quantum)

        val page = leaderboard("?kind=classic&limit=2&offset=1").entries
        assertEquals(listOf("dave", "carol"), page.map { it.user.username })
        assertEquals(listOf(2, 3), page.map { it.rank })
        assertTrue(leaderboard("?kind=QUANTUM").entries.isEmpty())
    }

    @Test
    fun profileShowsRatingsPresenceAndRecentGames() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        assertEquals(HttpStatusCode.OK, setDisplayName(alice, "Alice").status)
        val games = (1..12).map { activeGame(alice, bob) }
        foolsMate(alice, bob, games.last().id)

        val profile = profile("ALICE")
        assertEquals("alice", profile.user.username)
        assertEquals("Alice", profile.user.name)
        assertNull(profile.user.rating, "the rating is bound to a game's kind")
        assertEquals(1480, profile.ratings.classic?.rating)
        assertEquals("2026-10-07T10:00:00Z", profile.memberSince)
        assertEquals("2026-10-07T10:00:00Z", profile.lastSeen)
        assertEquals(10, profile.recentGames.size)
        // Every game was created at the same fixed instant, so the id breaks the tie, newest first.
        assertEquals(profile.recentGames.map { it.id }.sortedDescending(), profile.recentGames.map { it.id })
        assertTrue(profile.recentGames.all { it.white?.username == "alice" })

        profileResponse("nobody").assertError(HttpStatusCode.NotFound, "not_found")
        profileResponse(guest().user.username).assertError(HttpStatusCode.NotFound, "not_found")

        val bot = profile(botNamed(EngineLevel.EXPERT))
        assertTrue(bot.user.bot)
        assertEquals(EngineLevel.EXPERT, bot.user.botLevel)
        assertNull(bot.lastSeen)
        assertEquals(PlayerRatings.NONE, bot.ratings)
        assertTrue(bot.recentGames.isEmpty())
    }

    @Test
    fun profileShowsOnlyWhatThePublicListingShows() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val open = createGame(alice)
        val publicActive = activeGame(alice, bob)
        val publicBot = botGame(alice, visibility = Visibility.PUBLIC)
        val accepted = join(bob, invite(alice, "bob").id)
        val privateOpen = createGame(alice, visibility = Visibility.PRIVATE)
        val privateActive = join(bob, createGame(alice, visibility = Visibility.PRIVATE).id)
        val privateBot = botGame(alice, visibility = Visibility.PRIVATE)
        val pending = invite(alice, "bob", visibility = Visibility.PUBLIC)

        // A stranger (the profile needs no token) learns the ids of public games only: not of a
        // private game, whose link is the only way in, nor of an invitation nobody accepted yet.
        val shown = profile("alice").recentGames.map { it.id }.toSet()
        assertEquals(setOf(open.id, publicActive.id, publicBot.id, accepted.id), shown)
        for (hidden in listOf(privateOpen, privateActive, privateBot, pending)) {
            assertTrue(hidden.id !in shown, "game ${hidden.id} must not be on the public profile")
        }
        // Bob's profile shows the public games he is seated in; not the private one, nor the invitation he has not answered.
        assertEquals(setOf(publicActive.id, accepted.id), profile("bob").recentGames.map { it.id }.toSet())
        // The owner's own listing still has everything.
        assertEquals(8, myGames(alice).size)
    }

    @Test
    fun botsEndpointListsTheRoster() = serverTest {
        val bots = client.get("/api/bots").body<BotsResponse>().bots
        val roster = BotRoster.specs.filter { !it.legacy }
        assertEquals(8, roster.size)
        assertEquals(roster.map { it.username }, bots.map { it.user.username })
        assertEquals(roster.map { it.level }, bots.map { it.user.botLevel })
        assertEquals(roster.map { it.displayName }, bots.map { it.user.name })
        assertTrue(bots.all { it.user.bot })
        assertTrue(bots.all { it.ratings == PlayerRatings.NONE })
        assertEquals(EngineLevel.entries.toSet(), bots.map { it.user.botLevel }.toSet(), "every level is represented")
        assertTrue(bots.none { it.user.username == BotRoster.COMPUTER }, "the legacy account is not offered")

        val json = client.get("/api/bots").bodyAsText()
        assertTrue("\"botLevel\":\"NOVICE\"" in json, json)
    }

    @Test
    fun displayNameIsValidatedAndTrimmed() = serverTest {
        val alice = token("alice")
        assertNull(me(alice).name)

        val updated = setDisplayName(alice, "  Alice W  ")
        assertEquals(HttpStatusCode.OK, updated.status, updated.bodyAsString())
        assertEquals("Alice W", me(alice).name)
        assertEquals("Alice W", createGame(alice).white?.name)
        assertEquals("Alice W", login("alice").body<ru.werelaxe.chess.server.dto.AuthResponse>().user.name)

        setDisplayName(alice, "").assertError(HttpStatusCode.BadRequest, "validation")
        setDisplayName(alice, "   ").assertError(HttpStatusCode.BadRequest, "validation")
        setDisplayName(alice, "x".repeat(41)).assertError(HttpStatusCode.BadRequest, "validation")
        patchMe(alice, """{"displayName":42}""").assertError(HttpStatusCode.BadRequest, "validation")
        patchMe(alice, """{"displayName":["x"]}""").assertError(HttpStatusCode.BadRequest, "validation")
        // Nothing invisible: NUL (which PostgreSQL rejects outright), other control characters,
        // zero-width and bidirectional formatting characters, soft hyphens, line separators.
        for (name in listOf("a\\u0000b", "a\\nb", "a\\tb", "\\u200b", "a\\u200bb", "\\u202eevil", "a\\u2066b", "\\u00adAlice", "a\\u2028b")) {
            patchMe(alice, """{"displayName":"$name"}""").assertError(HttpStatusCode.BadRequest, "validation")
        }
        // Something must be left once rendered: at least one letter or digit.
        for (name in listOf("---", "!!! ???", "\\u00a0", " \\u3000 ")) {
            patchMe(alice, """{"displayName":"$name"}""").assertError(HttpStatusCode.BadRequest, "validation")
        }
        assertEquals("Alice W", me(alice).name, "rejected patches change nothing")

        for (name in listOf("Zoë Ærø", "Alice-2 (Wonderland)", "42", "A")) {
            val accepted = setDisplayName(alice, name)
            assertEquals(HttpStatusCode.OK, accepted.status, accepted.bodyAsString())
            assertEquals(name, me(alice).name)
        }
        assertEquals(HttpStatusCode.OK, setDisplayName(alice, "x".repeat(40)).status)
        val both = patchMe(alice, """{"locale":"ru","displayName":"Alice"}""")
        assertEquals(HttpStatusCode.OK, both.status, both.bodyAsString())
        val profile = me(alice)
        assertEquals("Alice", profile.name)
        assertEquals("ru", profile.locale)

        // A patch of the locale alone leaves the name alone, and vice versa.
        assertEquals(HttpStatusCode.OK, setLocale(alice, null).status)
        assertEquals("Alice", me(alice).name)
        assertEquals(HttpStatusCode.OK, setDisplayName(alice, null).status)
        assertNull(me(alice).name)
        assertNull(me(alice).locale)
        val json = client.get("/api/auth/me") { bearerAuth(alice) }.bodyAsText()
        assertTrue("\"name\":null" in json, json)
    }

    @Test
    fun lastSeenIsRefreshedAtMostEveryFiveMinutes() = serverTest {
        val alice = token("alice")
        suspend fun lastSeen() = assertNotNull(repositories.users.findByUsername("alice")).lastSeenAt
        assertNull(lastSeen(), "registering is not an authenticated request")

        me(alice)
        assertEquals(TEST_START, lastSeen())
        clock.advance(Duration.ofMinutes(2))
        me(alice)
        assertEquals(TEST_START, lastSeen(), "a request within five minutes is not written")
        clock.advance(Duration.ofMinutes(3))
        myGames(alice)
        assertEquals(TEST_START.plus(Duration.ofMinutes(5)), lastSeen())
        assertEquals("2026-10-07T10:05:00Z", profile("alice").lastSeen)
    }
}
