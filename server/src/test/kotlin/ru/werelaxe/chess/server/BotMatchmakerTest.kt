package ru.werelaxe.chess.server

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.server.dto.ColorChoice
import ru.werelaxe.chess.server.dto.CreateGameRequest
import ru.werelaxe.chess.server.dto.GamesResponse
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.model.GameRecord
import ru.werelaxe.chess.server.model.User
import ru.werelaxe.chess.server.model.Visibility
import ru.werelaxe.chess.server.repository.GameRepository
import ru.werelaxe.chess.server.repository.UserRepository
import ru.werelaxe.chess.server.repository.memory.InMemoryStore
import ru.werelaxe.chess.server.service.BotMatchmaker.Action
import ru.werelaxe.chess.server.service.BotRoster
import ru.werelaxe.chess.server.service.MoveProvider
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A game repository whose listings can be scripted with stale records, as the real one returns
 * when the game changes between the matchmaker's listing and its action.
 */
private class StaleListings(private val delegate: GameRepository) : GameRepository by delegate {
    var invitations: List<GameRecord>? = null
    var openGames: List<GameRecord>? = null

    override suspend fun listInvitationsBy(creatorIds: Collection<Long>, createdSince: Instant?): List<GameRecord> =
        invitations ?: delegate.listInvitationsBy(creatorIds, createdSince)

    override suspend fun listOpenGamesCreatedBetween(after: Instant, before: Instant, limit: Int): List<GameRecord> =
        openGames ?: delegate.listOpenGamesCreatedBetween(after, before, limit)
}

/** A user repository that reports scripted people as recently seen, whether or not their rows exist. */
private class ScriptedPresence(private val delegate: UserRepository) : UserRepository by delegate {
    var seen: List<User>? = null

    override suspend fun listSeenSince(since: Instant, limit: Int): List<User> = seen ?: delegate.listSeenSince(since, limit)
}

class BotMatchmakerTest {
    private val firstBot = BotRoster.specs.first { !it.legacy }.username

    private suspend fun TestContext.openGames(): List<ru.werelaxe.chess.server.dto.GameSummary> =
        client.get("/api/games?filter=open").body<GamesResponse>().games

    @Test
    fun createsOpenGamesUpToTheTarget() = serverTest(random = ScriptedRandom()) {
        val first = assertIs<Action.Created>(services.matchmaker.tick())
        val game = game(first.gameId)
        assertEquals(GamePhase.WAITING, game.status)
        assertEquals(Visibility.PUBLIC, game.visibility)
        assertTrue(game.rated)
        assertEquals(firstBot, game.creator.username)
        assertTrue(game.creator.bot)
        assertEquals(GameKind.CLASSIC, game.kind)

        assertIs<Action.Created>(services.matchmaker.tick())
        assertEquals(2, openGames().size)
        assertEquals(Action.Nothing, services.matchmaker.tick(), "the target is reached and the dice say no")
        assertEquals(2, openGames().size)

        // A human's open game does not count towards the bots' target.
        val alice = token("alice")
        createGame(alice)
        assertEquals(Action.Nothing, services.matchmaker.tick())
        join(alice, openGames().first { it.creator.bot }.id)
        assertIs<Action.Created>(services.matchmaker.tick())
    }

    @Test
    fun joinsAGameThatHasWaitedLongEnough() = serverTest(
        config = TEST_CONFIG.copy(botOpenGamesTarget = 0),
        random = ScriptedRandom(),
    ) {
        val random = this.random as ScriptedRandom
        val alice = token("alice")
        val waiting = createGame(alice, color = ColorChoice.WHITE)

        random.rolls.addAll(listOf(JOIN, NO_JOIN))
        assertEquals(Action.Nothing, services.matchmaker.tick(), "too fresh to join")
        clock.advance(Duration.ofMinutes(9))
        random.rolls.addAll(listOf(NO_JOIN, NO_JOIN))
        assertEquals(Action.Nothing, services.matchmaker.tick(), "the dice said no")
        random.rolls.addAll(listOf(JOIN))
        assertEquals(Action.Joined(waiting.id), services.matchmaker.tick())

        val joined = game(waiting.id)
        assertEquals(GamePhase.ACTIVE, joined.status)
        assertEquals("alice", joined.white?.username)
        assertEquals(firstBot, joined.black?.username)
        assertTrue(joined.rated)
        assertTrue(openGames().isEmpty())
    }

    @Test
    fun twoBotsPlayEachOtherAtTheBotPace() {
        val thinks = CopyOnWriteArrayList<Pair<Int, EngineLevel>>()
        val opening = listOf(normal("e2e4"), normal("e7e5"), normal("g1f3"), normal("b8c6"))
        val scripted = object : MoveProvider {
            override suspend fun chooseMove(gameId: String, kind: GameKind, level: EngineLevel, moves: List<GameMove>): GameMove? {
                thinks += moves.size to level
                return opening.getOrNull(moves.size)
            }
        }
        serverTest(config = TEST_CONFIG.copy(botOpenGamesTarget = 1), random = ScriptedRandom(), moveProvider = scripted) {
            val random = this.random as ScriptedRandom
            val created = assertIs<Action.Created>(services.matchmaker.tick())
            assertEquals(firstBot, game(created.gameId).black?.username, "tails: the creator plays black")
            clock.advance(Duration.ofMinutes(9))
            random.rolls.add(JOIN)
            assertEquals(Action.Joined(created.gameId), services.matchmaker.tick())

            val active = game(created.gameId)
            assertEquals(GamePhase.ACTIVE, active.status)
            val white = assertNotNull(active.white)
            val black = assertNotNull(active.black)
            assertTrue(white.bot && black.bot)
            assertNotEquals(white.username, black.username, "a bot never joins its own game")
            assertEquals(EngineLevel.EASY, white.botLevel)
            assertEquals(EngineLevel.NOVICE, black.botLevel)

            assertEquals(4, awaitStoredMoves(created.gameId, 4))
            assertEquals(opening, game(created.gameId).moves)
            assertEquals(listOf(EngineLevel.EASY, EngineLevel.NOVICE, EngineLevel.EASY, EngineLevel.NOVICE), thinks.sortedBy { it.first }.take(4).map { it.second })
        }
    }

    @Test
    fun invitesARecentlySeenHuman() = serverTest(
        config = TEST_CONFIG.copy(botOpenGamesTarget = 0),
        random = ScriptedRandom(),
    ) {
        val random = this.random as ScriptedRandom
        val alice = token("alice")
        token("bob")
        random.rolls.addAll(listOf(NO_JOIN, INVITE))
        assertEquals(Action.Nothing, services.matchmaker.tick(), "nobody has been seen yet")

        me(alice)
        random.rolls.addAll(listOf(NO_JOIN, INVITE))
        val invited = assertIs<Action.Invited>(services.matchmaker.tick())
        assertEquals(me(alice).id, invited.userId)
        val invitation = invitations(alice).incoming.single()
        assertEquals(invited.gameId, invitation.id)
        assertTrue(invitation.creator.bot)
        assertTrue(invitation.rated)
        assertEquals(GamePhase.WAITING, invitation.status)
        assertTrue(openGames().isEmpty(), "a pending invitation is not an open game")

        random.rolls.addAll(listOf(NO_JOIN, INVITE))
        assertEquals(Action.Nothing, services.matchmaker.tick(), "one pending invitation per human")

        assertEquals(HttpStatusCode.NoContent, decline(alice, invitation.id).status)
        random.rolls.addAll(listOf(NO_JOIN, INVITE))
        assertEquals(Action.Nothing, services.matchmaker.tick(), "a declined invitation is not repeated for hours")

        clock.advance(Duration.ofHours(7))
        me(alice)
        random.rolls.addAll(listOf(NO_JOIN, INVITE))
        assertIs<Action.Invited>(services.matchmaker.tick())

        clock.advance(Duration.ofDays(8))
        random.rolls.addAll(listOf(NO_JOIN, INVITE))
        assertEquals(Action.Nothing, services.matchmaker.tick(), "nobody was seen within a week")
    }

    @Test
    fun expiresOldInvitations() = serverTest(
        config = TEST_CONFIG.copy(botOpenGamesTarget = 0),
        random = ScriptedRandom(),
    ) {
        val random = this.random as ScriptedRandom
        val alice = token("alice")
        me(alice)
        random.rolls.addAll(listOf(NO_JOIN, INVITE))
        val invited = assertIs<Action.Invited>(services.matchmaker.tick())

        clock.advance(Duration.ofMinutes(59))
        services.matchmaker.tick()
        assertEquals(HttpStatusCode.OK, getGame(invited.gameId).status)
        clock.advance(Duration.ofMinutes(2))
        services.matchmaker.tick()
        getGame(invited.gameId).assertError(HttpStatusCode.NotFound, "not_found")
        assertTrue(invitations(alice).incoming.isEmpty())
    }

    @Test
    fun respectsTheActiveGamesCap() = serverTest(
        config = TEST_CONFIG.copy(botMaxActiveGames = 1),
        random = ScriptedRandom(),
    ) {
        val alice = token("alice")
        val roster = BotRoster.specs.filter { !it.legacy }
        val games = roster.map { invite(alice, it.username, color = ColorChoice.WHITE) }
        assertTrue(games.all { it.status == GamePhase.ACTIVE })
        assertEquals(Action.Nothing, services.matchmaker.tick(), "every bot is busy")
        assertTrue(openGames().isEmpty())

        assertEquals(HttpStatusCode.OK, resign(alice, games[2].id).status)
        val created = assertIs<Action.Created>(services.matchmaker.tick())
        assertEquals(roster[2].username, game(created.gameId).creator.username, "the freed bot is the only one available")
    }

    @Test
    fun expiryNeverResignsAnInvitationAcceptedMeanwhile() {
        val store = InMemoryStore().repositories()
        val listings = StaleListings(store.games)
        serverTest(
            config = TEST_CONFIG.copy(botOpenGamesTarget = 0),
            random = ScriptedRandom(),
            repositories = store.copy(games = listings),
        ) {
            val random = this.random as ScriptedRandom
            val alice = token("alice")
            me(alice)
            random.rolls.addAll(listOf(NO_JOIN, INVITE))
            val invited = assertIs<Action.Invited>(services.matchmaker.tick())
            val pending = assertNotNull(repositories.games.findById(invited.gameId))

            // Alice accepts just as the invitation reaches its TTL; the tick still lists it as pending.
            clock.advance(Duration.ofMinutes(61))
            assertEquals(GamePhase.ACTIVE, join(alice, invited.gameId).status)
            listings.invitations = listOf(pending)
            assertEquals(Action.Nothing, services.matchmaker.tick())

            val untouched = game(invited.gameId)
            assertEquals(GamePhase.ACTIVE, untouched.status, "the bot must not resign the game its invitee accepted")
            assertNull(untouched.result)
        }
    }

    @Test
    fun abandonedOpenGamesExpireAndAreNeverJoined() = serverTest(
        config = TEST_CONFIG.copy(botOpenGamesTarget = 0, openGameTtlHours = 24),
        random = ScriptedRandom(),
    ) {
        val random = this.random as ScriptedRandom
        val alice = token("alice")
        val bot = services.bots.roster.last()
        val stale = createGame(alice)
        val privateOpen = createGame(alice, visibility = Visibility.PRIVATE)
        val botOpen = services.games.create(services.bots.principal(bot), CreateGameRequest(GameKind.CLASSIC, color = ColorChoice.WHITE))
        clock.advance(Duration.ofHours(23))
        val fresh = createGame(alice)
        clock.advance(Duration.ofMinutes(9))

        // Every open game has waited long enough; the newest one is the first candidate.
        random.rolls.add(JOIN)
        assertEquals(Action.Joined(fresh.id), services.matchmaker.tick())
        assertEquals(GamePhase.ACTIVE, game(fresh.id).status)
        assertEquals(HttpStatusCode.OK, getGame(stale.id).status, "younger than the TTL: kept")

        // A day after their creation the others are abandoned lobbies: deleted, not joined.
        clock.advance(Duration.ofHours(1))
        random.rolls.add(JOIN)
        assertEquals(Action.Nothing, services.matchmaker.tick())
        getGame(stale.id).assertError(HttpStatusCode.NotFound, "not_found")
        getGame(botOpen.id).assertError(HttpStatusCode.NotFound, "not_found")
        assertTrue(myGames(alice).none { it.id == stale.id })
        assertEquals(GamePhase.WAITING, game(privateOpen.id).status, "a private game is a link its creator shares; it is not a lobby")
        assertTrue(openGames().isEmpty())
    }

    @Test
    fun joinRaceIsBenignAndTheTickGoesOn() {
        val store = InMemoryStore().repositories()
        val listings = StaleListings(store.games)
        serverTest(
            config = TEST_CONFIG.copy(botOpenGamesTarget = 0),
            random = ScriptedRandom(),
            repositories = store.copy(games = listings),
        ) {
            val random = this.random as ScriptedRandom
            val alice = token("alice")
            val bob = token("bob")
            val open = createGame(alice)
            val listed = assertNotNull(repositories.games.findById(open.id))
            clock.advance(Duration.ofMinutes(9))
            // Bob joins between the listing and the bot's join.
            join(bob, open.id)
            listings.openGames = listOf(listed)
            me(alice)

            random.rolls.addAll(listOf(JOIN, INVITE))
            assertIs<Action.Invited>(services.matchmaker.tick(), "the failed join is no error: the tick falls through to the invitation")
            val active = game(open.id)
            assertEquals(GamePhase.ACTIVE, active.status)
            assertEquals("bob", active.black?.username)
        }
    }

    @Test
    fun invitationThatCannotBeCreatedIsBenign() {
        val store = InMemoryStore().repositories()
        val presence = ScriptedPresence(store.users)
        serverTest(
            config = TEST_CONFIG.copy(botOpenGamesTarget = 0),
            random = ScriptedRandom(),
            repositories = store.copy(users = presence),
        ) {
            val random = this.random as ScriptedRandom
            // Seen a moment ago, but the account is gone by the time the bot invites it.
            presence.seen = listOf(
                User(999, "ghost", passwordHash = null, isGuest = false, isBot = false, createdAt = TEST_START, locale = null, displayName = null, botLevel = null, lastSeenAt = TEST_START),
            )
            random.rolls.addAll(listOf(NO_JOIN, INVITE))
            assertEquals(Action.Nothing, services.matchmaker.tick())
            assertTrue(openGames().isEmpty())
        }
    }
}
