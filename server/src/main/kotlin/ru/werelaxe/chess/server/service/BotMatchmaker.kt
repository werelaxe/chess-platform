package ru.werelaxe.chess.server.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.server.dto.ColorChoice
import ru.werelaxe.chess.server.dto.CreateGameRequest
import ru.werelaxe.chess.server.dto.Opponent
import ru.werelaxe.chess.server.model.GameRecord
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
 * Keeps the lobby alive with the bots: on every tick, wakes the bots in the games where one is
 * on turn, deletes the bot invitations and the public open games that expired, and then does
 * one thing (docs/ARCHITECTURE.md section 2.10). A random bot creates an open game while fewer
 * than the target exist; otherwise, with probability 1/2, a random bot joins a public game that
 * has waited long enough but not too long (another bot's included); otherwise, with
 * probability 1/4, a random bot invites a recently seen human. Bots at the active-games cap sit
 * the tick out. Every choice goes through [random] and [clock], so tests drive [tick] directly
 * with scripted values.
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
        data class Created(val gameId: String) : Action

        data class Joined(val gameId: String) : Action

        data class Invited(val gameId: String, val userId: Long) : Action

        data object Nothing : Action
    }

    /** Ticks forever after random pauses; cancelled with the application. */
    suspend fun loop() {
        while (true) {
            val tickMin = settings.tickMin
            val span = (settings.tickMax - tickMin).coerceAtLeast(Duration.ZERO).toMillis()
            delay((tickMin.toMillis() + random.nextLong(span + 1)).milliseconds)
            try {
                val action = tick()
                if (action != Action.Nothing) log.info("Matchmaking: {}", action)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Matchmaking tick failed", e)
            }
        }
    }

    suspend fun tick(): Action {
        if (!settings.matchmaking) return Action.Nothing
        val openGamesTarget = settings.openGamesTarget
        val joinAfter = settings.joinAfter
        val invitationTtl = settings.invitationTtl
        val maxActiveGames = settings.maxActiveGames
        val now = clock.instant()
        games.wakeBots()
        expireInvitations(now, invitationTtl)
        expireOpenGames(now)
        val available = availableBots(maxActiveGames)
        if (available.isEmpty()) return Action.Nothing
        if (repository.countOpenGamesBy(bots.ids) < openGamesTarget) return createOpenGame(available.pick())
        if (random.nextDouble() < JOIN_PROBABILITY) joinOldGame(available, now, joinAfter)?.let { return it }
        if (random.nextDouble() < INVITE_PROBABILITY) inviteHuman(available, now)?.let { return it }
        return Action.Nothing
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

    private suspend fun createOpenGame(creator: User): Action {
        val created = games.create(bots.principal(creator), CreateGameRequest(randomKind(), Visibility.PUBLIC, ColorChoice.RANDOM, rated = true))
        return Action.Created(created.id)
    }

    /**
     * Joins a game that has waited at least [joinAfter] but is younger than the TTL, preferring
     * the newest: that is the one whose creator is most likely still there.
     */
    private suspend fun joinOldGame(available: List<User>, now: Instant, joinAfter: Duration): Action? {
        val candidates = repository.listOpenGamesCreatedBetween(now - openGameTtl, now - joinAfter, JOIN_CANDIDATES)
        if (candidates.isEmpty()) return null
        val game: GameRecord = candidates.pick()
        val joiner = available.filter { it.id != game.creatorId }.takeIf { it.isNotEmpty() }?.pick() ?: return null
        return try {
            games.join(bots.principal(joiner), game.id)
            Action.Joined(game.id)
        } catch (e: ApiException) {
            // Somebody joined it or its creator cancelled it since the listing: a benign race.
            log.debug("Bot {} could not join game {}: {}", joiner.username, game.id, e.code)
            null
        }
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

    private fun randomKind(): GameKind = GameKind.entries.pick()

    private fun <T> List<T>.pick(): T = this[random.nextInt(size)]

    companion object {
        const val JOIN_PROBABILITY = 0.5
        const val INVITE_PROBABILITY = 0.25

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
