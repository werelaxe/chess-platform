package ru.werelaxe.chess.server.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.server.dto.ColorChoice
import ru.werelaxe.chess.server.dto.CreateGameRequest
import ru.werelaxe.chess.server.dto.Opponent
import ru.werelaxe.chess.server.model.User
import ru.werelaxe.chess.server.model.Visibility
import ru.werelaxe.chess.server.repository.GameRepository
import ru.werelaxe.chess.server.repository.UserRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Keeps the lobby alive with the bots (docs/ARCHITECTURE.md section 2.10). On every tick it
 * wakes the bots in the games where one is on turn, deletes the bot invitations and the public
 * open games that expired, and then, while fewer bot-versus-bot games than the live target are
 * in progress, starts up to two of them (two distinct bots: one creates a public rated game,
 * the other joins it). The tick ends with one lobby action: a random bot creates an open game
 * while fewer than the open-games target exist; otherwise a bot joins the oldest public game
 * that has waited long enough but not too long (another bot's included), without any dice;
 * otherwise, with probability 1/4, a random bot invites a recently seen human. Bots at the
 * active-games cap sit the tick out. Every choice goes through [random] and [clock], so tests
 * drive [tick] directly with scripted values.
 *
 * The tunables come from [settings] (section 2.11): the targets are read at the start of
 * every tick and the sleep range before every pause, so a change applies without a restart.
 * With `bot.matchmaking` off a tick does nothing at all, and the loop keeps running so that
 * switching it back on takes effect at the next tick. [openGameTtl] is the age at which a
 * public open game nobody joined is deleted as an abandoned lobby, whoever created it.
 */
class BotMatchmaker(
    private val games: GameService,
    private val repository: GameRepository,
    private val users: UserRepository,
    private val bots: Bots,
    private val settings: Settings,
    private val openGameTtl: Duration,
    private val random: Random,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(BotMatchmaker::class.java)

    /** When each human last received a bot invitation, so that a declined one is not repeated at once. */
    private val invitedAt = HashMap<Long, Instant>()

    /** What a tick did; mostly for the tests. */
    sealed interface Action {
        /** A bot-versus-bot game started towards the live target. */
        data class Started(val gameId: String) : Action

        data class Created(val gameId: String) : Action

        data class Joined(val gameId: String) : Action

        data class Invited(val gameId: String, val userId: Long) : Action
    }

    /** Ticks forever after random pauses; cancelled with the application. */
    suspend fun loop() {
        while (true) {
            val tickMin = settings.tickMin
            val span = (settings.tickMax - tickMin).coerceAtLeast(Duration.ZERO).toMillis()
            delay((tickMin.toMillis() + random.nextLong(span + 1)).milliseconds)
            try {
                for (action in tick()) log.info("Matchmaking: {}", action)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Matchmaking tick failed", e)
            }
        }
    }

    /** Does the tick's work and returns it in order: the live games started, then the lobby action if any. */
    suspend fun tick(): List<Action> {
        if (!settings.matchmaking) return emptyList()
        val openGamesTarget = settings.openGamesTarget
        val liveGamesTarget = settings.liveGamesTarget
        val joinAfter = settings.joinAfter
        val invitationTtl = settings.invitationTtl
        val maxActiveGames = settings.maxActiveGames
        val now = clock.instant()
        games.wakeBots()
        expireInvitations(now, invitationTtl)
        expireOpenGames(now)
        val actions = ArrayList<Action>()
        actions += startLiveGames(liveGamesTarget, maxActiveGames)
        // The games just started count towards the cap, so the available bots are read afresh.
        val available = availableBots(maxActiveGames)
        if (available.isEmpty()) return actions
        if (repository.countOpenGamesBy(bots.ids) < openGamesTarget) {
            actions += createOpenGame(available.pick())
        } else {
            val action = joinOldestGame(available, now, joinAfter)
                ?: if (random.nextDouble() < INVITE_PROBABILITY) inviteHuman(available, now) else null
            if (action != null) actions += action
        }
        return actions
    }

    private suspend fun expireInvitations(now: Instant, invitationTtl: Duration) {
        val pending = repository.listInvitationsBy(bots.ids, createdSince = null)
        for (invitation in pending) {
            if (Duration.between(invitation.createdAt, now) < invitationTtl) continue
            val creator = bots[invitation.creatorId] ?: continue
            try {
                games.cancelInvitation(bots.principal(creator), invitation.id)
            } catch (e: ApiException) {
                // Accepted (409) or declined (404) since the listing: nothing left to expire.
                log.debug("Invitation {} not expired: {}", invitation.id, e.code)
            }
        }
    }

    /** Public open games nobody joined for [openGameTtl] are abandoned lobbies; they are deleted. */
    private suspend fun expireOpenGames(now: Instant) {
        val cutoff = now - openGameTtl
        for (game in repository.listOpenGamesCreatedBefore(cutoff, EXPIRE_BATCH)) {
            if (games.expireOpenGame(game.id, cutoff)) log.info("Expired open game {} created at {}", game.id, game.createdAt)
        }
    }

    /** The roster bots with fewer than [maxActiveGames] games in progress. */
    private suspend fun availableBots(maxActiveGames: Int): List<User> {
        val active = HashMap<Long, Int>()
        for (game in repository.listActiveGamesOf(bots.ids)) {
            for (seat in listOfNotNull(game.whiteId, game.blackId)) {
                if (bots.isBot(seat)) active.merge(seat, 1, Int::plus)
            }
        }
        return bots.roster.filter { (active[it.id] ?: 0) < maxActiveGames }
    }

    /**
     * Starts bot-versus-bot games while fewer than [target] are in progress, at most
     * [LIVE_GAMES_PER_TICK] per tick so that a raised target fills up over a few ticks rather
     * than all at once. Each game takes a slot of both bots, so the available bots are read
     * again before every game; two distinct ones are needed.
     */
    private suspend fun startLiveGames(target: Int, maxActiveGames: Int): List<Action> {
        if (target <= 0) return emptyList()
        val started = ArrayList<Action>()
        var live = repository.countActiveGamesAmong(bots.ids)
        repeat(LIVE_GAMES_PER_TICK) {
            if (live >= target) return started
            val available = availableBots(maxActiveGames)
            if (available.size < 2) return started
            val creator = available.pick()
            val joiner = available.filter { it.id != creator.id }.pick()
            val created = games.create(bots.principal(creator), openGameRequest())
            try {
                games.join(bots.principal(joiner), created.id)
            } catch (e: ApiException) {
                // A human grabbed the seat between the two calls: a game all the same, just not a live one.
                log.debug("Bot {} could not join the game {} of {}: {}", joiner.username, created.id, creator.username, e.code)
                return@repeat
            }
            started += Action.Started(created.id)
            live++
        }
        return started
    }

    private suspend fun createOpenGame(creator: User): Action {
        val created = games.create(bots.principal(creator), openGameRequest())
        return Action.Created(created.id)
    }

    /**
     * Joins the oldest game that has waited at least [joinAfter] but is younger than the TTL:
     * the one whose creator has been kept waiting the longest. A game the only available bot
     * created itself, or one joined or cancelled since the listing (a benign race, logged at
     * DEBUG), is passed over for the next oldest.
     */
    private suspend fun joinOldestGame(available: List<User>, now: Instant, joinAfter: Duration): Action? {
        val candidates = repository.listOpenGamesCreatedBetween(now - openGameTtl, now - joinAfter, JOIN_CANDIDATES)
        for (game in candidates) {
            val joiner = available.filter { it.id != game.creatorId }.takeIf { it.isNotEmpty() }?.pick() ?: continue
            try {
                games.join(bots.principal(joiner), game.id)
                return Action.Joined(game.id)
            } catch (e: ApiException) {
                log.debug("Bot {} could not join game {}: {}", joiner.username, game.id, e.code)
            }
        }
        return null
    }

    private suspend fun inviteHuman(available: List<User>, now: Instant): Action? {
        val cooldownStart = now - INVITATION_COOLDOWN
        val recentlyInvited = repository.listInvitationsBy(bots.ids, createdSince = cooldownStart)
            .mapNotNullTo(HashSet()) { it.inviteeId }
        invitedAt.entries.removeIf { it.value < cooldownStart }
        val humans = users.listSeenSince(now - ACTIVITY_WINDOW, INVITE_CANDIDATES)
            .filter { it.id !in recentlyInvited && it.id !in invitedAt }
        if (humans.isEmpty()) return null
        val human = humans.pick()
        val bot = available.pick()
        val request = CreateGameRequest(
            kind = randomKind(),
            visibility = Visibility.PUBLIC,
            color = ColorChoice.RANDOM,
            rated = true,
            opponent = Opponent.USER,
            opponentUsername = human.username,
        )
        return try {
            val created = games.create(bots.principal(bot), request)
            invitedAt[human.id] = now
            Action.Invited(created.id, human.id)
        } catch (e: ApiException) {
            // The account is gone or cannot be invited any more: nothing to do this tick.
            log.debug("Bot {} could not invite {}: {}", bot.username, human.username, e.code)
            null
        }
    }

    /** A public rated game open to anybody, of a random kind, in which the creator plays a random colour. */
    private fun openGameRequest() = CreateGameRequest(randomKind(), Visibility.PUBLIC, ColorChoice.RANDOM, rated = true)

    private fun randomKind(): GameKind = GameKind.entries.pick()

    private fun <T> List<T>.pick(): T = this[random.nextInt(size)]

    companion object {
        const val INVITE_PROBABILITY = 0.25

        /** Bot-versus-bot games started per tick at most, so that a raised live target fills up gradually. */
        const val LIVE_GAMES_PER_TICK = 2

        /** How recently a human must have been seen to be invited. */
        val ACTIVITY_WINDOW: Duration = Duration.ofDays(7)

        /** How long after an invitation from a bot a human is left alone. */
        val INVITATION_COOLDOWN: Duration = Duration.ofHours(6)
        private const val JOIN_CANDIDATES = 20
        private const val INVITE_CANDIDATES = 100

        /** Expired open games deleted per tick at most; the rest wait for the next one. */
        private const val EXPIRE_BATCH = 100
    }
}
