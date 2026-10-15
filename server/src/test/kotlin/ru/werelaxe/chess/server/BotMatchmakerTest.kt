package ru.werelaxe.chess.server

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.engine.ThinkPriority
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

/** The moves of a classic game as the bots play them with [openingProvider]. */
private val OPENING = listOf(normal("e2e4"), normal("e7e5"), normal("g1f3"), normal("b8c6"))

/**
 * A move provider that plays [opening] and then stops, so that the bots neither search for
 * real nor play on in the background; with an empty opening they sit still.
 */
private fun openingProvider(opening: List<GameMove>) = object : MoveProvider {
    override suspend fun chooseMove(
        gameId: String,
        kind: GameKind,
        level: EngineLevel,
        moves: List<GameMove>,
        priority: ThinkPriority,
    ): GameMove? = opening.getOrNull(moves.size)
}

class BotMatchmakerTest {
    private val firstBot = BotRoster.specs.first { !it.legacy }.username

    private suspend fun TestContext.openGames(): List<ru.werelaxe.chess.server.dto.GameSummary> =
        client.get("/api/games?filter=open").body<GamesResponse>().games

    private suspend fun TestContext.liveGames(): Int = repositories.games.countActiveGamesAmong(services.bots.ids)

    @Test
    fun createsOpenGamesUpToTheTarget() = serverTest(random = ScriptedRandom()) {
        val first = assertIs<Action.Created>(services.matchmaker.tick().single())
        val game = game(first.gameId)
        assertEquals(GamePhase.WAITING, game.status)
        assertEquals(Visibility.PUBLIC, game.visibility)
        assertTrue(game.rated)
        assertEquals(firstBot, game.creator.username)
        assertTrue(game.creator.bot)
        assertEquals(GameKind.CLASSIC, game.kind)

        assertIs<Action.Created>(services.matchmaker.tick().single())
        assertEquals(2, openGames().size)
        assertEquals(emptyList(), services.matchmaker.tick(), "the target is reached, nothing has waited long enough and the dice say no")
        assertEquals(2, openGames().size)

        // A human's open game does not count towards the bots' target.
        val alice = token("alice")
        createGame(alice)
        assertEquals(emptyList(), services.matchmaker.tick())
        join(alice, openGames().first { it.creator.bot }.id)
        assertIs<Action.Created>(services.matchmaker.tick().single())
    }

    @Test
    fun joinsTheOldestGameThatHasWaitedLongEnoughWithoutDice() = serverTest(
        config = TEST_CONFIG.copy(botOpenGamesTarget = 0),
        random = ScriptedRandom(),
    ) {
        val alice = token("alice")
        val bob = token("bob")
        val older = createGame(alice, color = ColorChoice.WHITE)
        clock.advance(Duration.ofMinutes(1))
        val newer = createGame(bob, color = ColorChoice.WHITE)

        assertEquals(emptyList(), services.matchmaker.tick(), "too fresh to join")
        clock.advance(Duration.ofMinutes(8))
        // No roll is queued: a join is not a matter of luck, and the game that waited longest goes first.
        assertEquals(listOf(Action.Joined(older.id)), services.matchmaker.tick())
        assertEquals(listOf(Action.Joined(newer.id)), services.matchmaker.tick())
        assertEquals(emptyList(), services.matchmaker.tick(), "nothing is left to join")

        val joined = game(older.id)
        assertEquals(GamePhase.ACTIVE, joined.status)
        assertEquals("alice", joined.white?.username)
        assertEquals(firstBot, joined.black?.username)
        assertTrue(joined.rated)
        val second = assertNotNull(game(newer.id).black?.username)
        assertNotEquals(firstBot, second, "the second game goes to an idle bot, not to the one already playing")
        assertTrue(openGames().isEmpty())
    }

    @Test
    fun passesOverTheGameOfTheOnlyAvailableBot() = serverTest(
        config = TEST_CONFIG.copy(botOpenGamesTarget = 0, botMaxActiveGames = 1),
        random = ScriptedRandom(),
    ) {
        val alice = token("alice")
        val roster = services.bots.roster
        // Every bot but the first is busy; the first has an open game of its own, older than Alice's.
        for (bot in roster.drop(1)) invite(alice, bot.username, color = ColorChoice.WHITE)
        val own = services.games.create(services.bots.principal(roster.first()), CreateGameRequest(GameKind.CLASSIC, color = ColorChoice.WHITE))
        clock.advance(Duration.ofMinutes(1))
        val alices = createGame(alice)
        clock.advance(Duration.ofMinutes(8))

        assertEquals(listOf(Action.Joined(alices.id)), services.matchmaker.tick(), "a bot never joins its own game: the next oldest is taken")
        assertEquals(GamePhase.WAITING, game(own.id).status)
    }

    @Test
    fun twoBotsPlayEachOtherAtTheBotPace() {
        val thinks = CopyOnWriteArrayList<Pair<Int, EngineLevel>>()
        val scripted = object : MoveProvider {
            override suspend fun chooseMove(
                gameId: String,
                kind: GameKind,
                level: EngineLevel,
                moves: List<GameMove>,
                priority: ThinkPriority,
            ): GameMove? {
                thinks += moves.size to level
                return OPENING.getOrNull(moves.size)
            }
        }
        serverTest(config = TEST_CONFIG.copy(botOpenGamesTarget = 1), random = ScriptedRandom(), moveProvider = scripted) {
            val created = assertIs<Action.Created>(services.matchmaker.tick().single())
            assertEquals(firstBot, game(created.gameId).black?.username, "tails: the creator plays black")
            clock.advance(Duration.ofMinutes(9))
            assertEquals(listOf(Action.Joined(created.gameId)), services.matchmaker.tick())

            val active = game(created.gameId)
            assertEquals(GamePhase.ACTIVE, active.status)
            val white = assertNotNull(active.white)
            val black = assertNotNull(active.black)
            assertTrue(white.bot && black.bot)
            assertNotEquals(white.username, black.username, "a bot never joins its own game")
            assertEquals(EngineLevel.EASY, white.botLevel)
            assertEquals(EngineLevel.NOVICE, black.botLevel)

            assertEquals(4, awaitStoredMoves(created.gameId, 4))
            assertEquals(OPENING, game(created.gameId).moves)
            assertEquals(listOf(EngineLevel.EASY, EngineLevel.NOVICE, EngineLevel.EASY, EngineLevel.NOVICE), thinks.sortedBy { it.first }.take(4).map { it.second })
            assertEquals(1, liveGames(), "a bot joining another bot's game counts as a live game")
        }
    }

    @Test
    fun liveTargetStartsBotVersusBotGamesThatArePlayedAtOnce() = serverTest(
        config = TEST_CONFIG.copy(botLiveGamesTarget = 2, botOpenGamesTarget = 1),
        random = ScriptedRandom(),
        moveProvider = openingProvider(OPENING),
    ) {
        val actions = services.matchmaker.tick()
        val started = actions.filterIsInstance<Action.Started>()
        assertEquals(2, started.size, "two games towards the target of two: $actions")
        assertIs<Action.Created>(actions.last(), "the tick goes on with the lobby: an open game is created too")
        for (action in started) {
            val live = game(action.gameId)
            assertEquals(GamePhase.ACTIVE, live.status)
            assertEquals(Visibility.PUBLIC, live.visibility)
            assertTrue(live.rated)
            assertEquals(GameKind.CLASSIC, live.kind)
            val white = assertNotNull(live.white)
            val black = assertNotNull(live.black)
            assertTrue(white.bot && black.bot)
            assertNotEquals(white.username, black.username, "two distinct bots")
            assertEquals(4, awaitStoredMoves(action.gameId, 4))
            assertEquals(OPENING, game(action.gameId).moves)
        }
        assertEquals(2, liveGames())
        assertEquals(1, openGames().size)
        assertEquals(emptyList(), services.matchmaker.tick(), "at the target nothing starts; the open game is too fresh to join")

        // A live game that ends is replaced at the next tick.
        val over = game(started.first().gameId)
        val white = services.bots.roster.single { it.username == over.white?.username }
        assertEquals(GamePhase.FINISHED, services.games.resign(services.bots.principal(white), over.id).status)
        assertEquals(1, liveGames())
        val replaced = assertIs<Action.Started>(services.matchmaker.tick().single())
        assertEquals(GamePhase.ACTIVE, game(replaced.gameId).status)
        assertEquals(2, liveGames())
    }

    @Test
    fun startsAtMostTwoLiveGamesPerTick() = serverTest(
        config = TEST_CONFIG.copy(botLiveGamesTarget = 5, botOpenGamesTarget = 0),
        random = ScriptedRandom(),
        moveProvider = openingProvider(emptyList()),
    ) {
        assertEquals(2, services.matchmaker.tick().count { it is Action.Started })
        assertEquals(2, liveGames())
        assertEquals(2, services.matchmaker.tick().count { it is Action.Started })
        assertEquals(4, liveGames())
        assertEquals(1, services.matchmaker.tick().count { it is Action.Started }, "the last one up to the target")
        assertEquals(5, liveGames())
        assertEquals(emptyList(), services.matchmaker.tick())
        assertEquals(5, liveGames())
    }

    @Test
    fun liveGamesGoToTheLeastBusyBots() = serverTest(
        config = TEST_CONFIG.copy(botLiveGamesTarget = 3, botOpenGamesTarget = 0),
        random = ScriptedRandom(),
        moveProvider = openingProvider(emptyList()),
    ) {
        val alice = token("alice")
        val roster = services.bots.roster
        assertEquals(8, roster.size)
        // The first two bots already play a human each; they must not be picked while the other six are idle.
        val busy = roster.take(2).map { it.username }.toSet()
        for (name in busy) invite(alice, name, color = ColorChoice.WHITE)

        val seats = HashMap<String, Int>()
        repeat(2) {
            for (action in services.matchmaker.tick().filterIsInstance<Action.Started>()) {
                val live = game(action.gameId)
                for (seat in listOfNotNull(live.white?.username, live.black?.username)) seats.merge(seat, 1, Int::plus)
            }
        }
        assertEquals(3, liveGames())
        assertEquals(6, seats.size, "three games spread over the six idle bots: $seats")
        assertTrue(seats.keys.none { it in busy }, "the busy bots sat this out: $seats")
        assertTrue(seats.values.all { it == 1 }, "nobody plays two live games while others play none: $seats")
    }

    @Test
    fun liveTargetZeroNeverStartsAGame() = serverTest(
        config = TEST_CONFIG.copy(botLiveGamesTarget = 0, botOpenGamesTarget = 0),
        random = ScriptedRandom(),
    ) {
        repeat(3) { assertEquals(emptyList(), services.matchmaker.tick()) }
        assertEquals(0, liveGames())
    }

    @Test
    fun liveGamesSkipTheBotsAtTheirCap() = serverTest(
        config = TEST_CONFIG.copy(botLiveGamesTarget = 2, botOpenGamesTarget = 0, botMaxActiveGames = 1),
        random = ScriptedRandom(),
        moveProvider = openingProvider(emptyList()),
    ) {
        val alice = token("alice")
        val roster = services.bots.roster
        // Every bot but the last two is busy.
        for (bot in roster.dropLast(2)) invite(alice, bot.username, color = ColorChoice.WHITE)

        val started = assertIs<Action.Started>(services.matchmaker.tick().single(), "one pair is free, so one game starts")
        val live = game(started.gameId)
        assertEquals(roster.takeLast(2).map { it.username }.toSet(), setOf(live.white?.username, live.black?.username))
        assertEquals(1, liveGames())
        assertEquals(emptyList(), services.matchmaker.tick(), "every bot is at its cap now")
        assertEquals(1, liveGames())
    }

    @Test
    fun humanLoadHoldsBotVersusBotGamesButNotTheHumans() = serverTest(
        config = TEST_CONFIG.copy(botLiveGamesTarget = 2, botOpenGamesTarget = 0, botHumanGamesLimit = 1),
        random = ScriptedRandom(),
        moveProvider = openingProvider(emptyList()),
    ) {
        val alice = token("alice")
        val bob = token("bob")
        // One game with a human is the limit: the load is high from here on.
        botGame(alice, color = ColorChoice.WHITE)
        val bot = services.bots.roster.last()
        val botsOpen = services.games.create(services.bots.principal(bot), CreateGameRequest(GameKind.CLASSIC, color = ColorChoice.WHITE))
        clock.advance(Duration.ofMinutes(1))
        val bobsOpen = createGame(bob)
        clock.advance(Duration.ofMinutes(8))

        // No live game starts, the bot's older lobby is passed over, and Bob gets his opponent.
        assertEquals(listOf(Action.Joined(bobsOpen.id)), services.matchmaker.tick())
        assertEquals(0, liveGames())
        assertEquals(GamePhase.ACTIVE, game(bobsOpen.id).status)
        assertEquals(GamePhase.WAITING, game(botsOpen.id).status)
        assertEquals(emptyList(), services.matchmaker.tick(), "the bot's lobby is still left alone")
        assertEquals(GamePhase.WAITING, game(botsOpen.id).status)
        assertEquals(1, services.humanLoad.humanGames(), "the count of the first tick is reused for ten seconds")
        assertEquals(2, services.humanLoad.refresh(), "Bob's game counts now")

        // Humans are still invited while the load is high.
        me(alice)
        (random as ScriptedRandom).rolls.add(INVITE)
        assertIs<Action.Invited>(services.matchmaker.tick().single())

        // The gate switched off, the next tick does everything it held back.
        services.settings.set("bot.human_games_limit", "0")
        val actions = services.matchmaker.tick()
        assertEquals(2, actions.count { it is Action.Started }, "the live games start: $actions")
        assertEquals(Action.Joined(botsOpen.id), actions.last(), "and the bot's lobby is joined: $actions")
        assertEquals(3, liveGames())
    }

    @Test
    fun humanLoadLimitZeroHoldsNothing() = serverTest(
        config = TEST_CONFIG.copy(botLiveGamesTarget = 1, botOpenGamesTarget = 0, botHumanGamesLimit = 0),
        random = ScriptedRandom(),
        moveProvider = openingProvider(emptyList()),
    ) {
        val alice = token("alice")
        repeat(3) { botGame(alice, color = ColorChoice.WHITE) }
        val bot = services.bots.roster.last()
        val botsOpen = services.games.create(services.bots.principal(bot), CreateGameRequest(GameKind.CLASSIC, color = ColorChoice.WHITE))
        clock.advance(Duration.ofMinutes(9))
        assertEquals(3, services.humanLoad.humanGames())
        val actions = services.matchmaker.tick()
        assertIs<Action.Started>(actions.first(), "$actions")
        assertEquals(Action.Joined(botsOpen.id), actions.last(), "$actions")
        assertEquals(2, liveGames())
    }

    @Test
    fun invitesARecentlySeenHuman() = serverTest(
        config = TEST_CONFIG.copy(botOpenGamesTarget = 0),
        random = ScriptedRandom(),
    ) {
        val random = this.random as ScriptedRandom
        val alice = token("alice")
        token("bob")
        random.rolls.add(INVITE)
        assertEquals(emptyList(), services.matchmaker.tick(), "nobody has been seen yet")

        me(alice)
        random.rolls.add(INVITE)
        val invited = assertIs<Action.Invited>(services.matchmaker.tick().single())
        assertEquals(me(alice).id, invited.userId)
        val invitation = invitations(alice).incoming.single()
        assertEquals(invited.gameId, invitation.id)
        assertTrue(invitation.creator.bot)
        assertTrue(invitation.rated)
        assertEquals(GamePhase.WAITING, invitation.status)
        assertTrue(openGames().isEmpty(), "a pending invitation is not an open game")

        random.rolls.add(INVITE)
        assertEquals(emptyList(), services.matchmaker.tick(), "one pending invitation per human")

        assertEquals(HttpStatusCode.NoContent, decline(alice, invitation.id).status)
        random.rolls.add(INVITE)
        assertEquals(emptyList(), services.matchmaker.tick(), "a declined invitation is not repeated for hours")

        clock.advance(Duration.ofHours(7))
        me(alice)
        random.rolls.add(INVITE)
        assertIs<Action.Invited>(services.matchmaker.tick().single())

        clock.advance(Duration.ofDays(8))
        random.rolls.add(INVITE)
        assertEquals(emptyList(), services.matchmaker.tick(), "nobody was seen within a week")
    }

    @Test
    fun expiresOldInvitations() = serverTest(
        config = TEST_CONFIG.copy(botOpenGamesTarget = 0),
        random = ScriptedRandom(),
    ) {
        val random = this.random as ScriptedRandom
        val alice = token("alice")
        me(alice)
        random.rolls.add(INVITE)
        val invited = assertIs<Action.Invited>(services.matchmaker.tick().single())

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
        assertEquals(emptyList(), services.matchmaker.tick(), "every bot is busy")
        assertTrue(openGames().isEmpty())

        assertEquals(HttpStatusCode.OK, resign(alice, games[2].id).status)
        val created = assertIs<Action.Created>(services.matchmaker.tick().single())
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
            random.rolls.add(INVITE)
            val invited = assertIs<Action.Invited>(services.matchmaker.tick().single())
            val pending = assertNotNull(repositories.games.findById(invited.gameId))

            // Alice accepts just as the invitation reaches its TTL; the tick still lists it as pending.
            clock.advance(Duration.ofMinutes(61))
            assertEquals(GamePhase.ACTIVE, join(alice, invited.gameId).status)
            listings.invitations = listOf(pending)
            assertEquals(emptyList(), services.matchmaker.tick())

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
        val alice = token("alice")
        val bot = services.bots.roster.last()
        val stale = createGame(alice)
        clock.advance(Duration.ofMinutes(1))
        val privateOpen = createGame(alice, visibility = Visibility.PRIVATE)
        val botOpen = services.games.create(services.bots.principal(bot), CreateGameRequest(GameKind.CLASSIC, color = ColorChoice.WHITE))
        clock.advance(Duration.ofHours(23))
        val fresh = createGame(alice)
        clock.advance(Duration.ofMinutes(9))

        // Every open game has waited long enough; the oldest one goes first.
        assertEquals(listOf(Action.Joined(stale.id)), services.matchmaker.tick())
        assertEquals(GamePhase.ACTIVE, game(stale.id).status)
        assertEquals(HttpStatusCode.OK, getGame(botOpen.id).status, "younger than the TTL: kept")

        // A day after its creation the bot's game is an abandoned lobby: deleted, not joined,
        // although it is the oldest; the younger human's game is joined instead.
        clock.advance(Duration.ofHours(1))
        assertEquals(listOf(Action.Joined(fresh.id)), services.matchmaker.tick())
        getGame(botOpen.id).assertError(HttpStatusCode.NotFound, "not_found")
        assertTrue(myGames(alice).none { it.id == botOpen.id })
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

            random.rolls.add(INVITE)
            assertIs<Action.Invited>(services.matchmaker.tick().single(), "the failed join is no error: the tick falls through to the invitation")
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
            random.rolls.add(INVITE)
            assertEquals(emptyList(), services.matchmaker.tick())
            assertTrue(openGames().isEmpty())
        }
    }
}
