package ru.werelaxe.chess.server.service

import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory
import ru.werelaxe.chess.server.dto.AdminBot
import ru.werelaxe.chess.server.dto.AdminBotDetails
import ru.werelaxe.chess.server.dto.AdminOverview
import ru.werelaxe.chess.server.dto.CreateBotRequest
import ru.werelaxe.chess.server.dto.GameCounts
import ru.werelaxe.chess.server.dto.PlayerRatings
import ru.werelaxe.chess.server.dto.UpdateBotRequest
import ru.werelaxe.chess.server.dto.UserRef
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.model.GameRecord
import ru.werelaxe.chess.server.model.User
import ru.werelaxe.chess.server.repository.GameRepository
import ru.werelaxe.chess.server.repository.RatingRepository
import ru.werelaxe.chess.server.repository.UserRepository
import java.time.Clock

/**
 * What the admin API (docs/ARCHITECTURE.md section 2.11) shows and changes: the overview and
 * the bots. Every change is logged; the settings are handled by [Settings] itself.
 */
class AdminService(
    private val users: UserRepository,
    private val games: GameRepository,
    private val ratings: RatingRepository,
    private val bots: Bots,
    private val settings: Settings,
    private val gameService: GameService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(AdminService::class.java)

    suspend fun overview(): AdminOverview {
        val accounts = users.countAccounts()
        val byPhase = games.countByPhase()
        return AdminOverview(
            users = accounts.users,
            guests = accounts.guests,
            bots = accounts.bots,
            games = GameCounts(
                waiting = byPhase[GamePhase.WAITING] ?: 0,
                active = byPhase[GamePhase.ACTIVE] ?: 0,
                finished = byPhase[GamePhase.FINISHED] ?: 0,
            ),
            activeBotGames = games.listActiveGamesOf(bots.ids).size,
            settings = settings.effective(),
        )
    }

    /** Every bot: the roster in its order, then the legacy ones. */
    suspend fun listBots(): List<AdminBot> = describe(bots.all)

    suspend fun bot(username: String): AdminBotDetails {
        val bot = findBot(username)
        val active = games.listActiveGamesOf(listOf(bot.id))
            .sortedWith(compareByDescending<GameRecord> { it.createdAt }.thenByDescending { it.id })
        return describe(listOf(bot)).single().withGames(
            activeGameList = gameService.toSummaries(active),
            recentGames = gameService.toSummaries(games.listForUser(bot.id, RECENT_GAMES, 0)),
        )
    }

    /** Creates a bot exactly like the roster's and refreshes the registry, so that it plays at once. */
    suspend fun createBot(request: CreateBotRequest): AdminBot {
        ProfileRules.validateUsername(request.username)
        val displayName = request.displayName.trim().also(ProfileRules::validateDisplayName)
        val created = users.createBot(request.username, displayName, request.level, clock.instant())
            ?: throw ApiException(HttpStatusCode.Conflict, "username_taken", "This username is already taken")
        bots.refresh()
        log.info("Admin: created bot {} ({}, {})", created.username, displayName, request.level)
        return describe(listOf(created)).single()
    }

    suspend fun updateBot(username: String, request: UpdateBotRequest): AdminBot {
        if (request.level == null && request.displayName == null) {
            throw ApiException.validation("Nothing to update: expected level and/or displayName")
        }
        val bot = findBot(username)
        val displayName = request.displayName?.trim()?.also(ProfileRules::validateDisplayName)
        val updated = users.updateBot(bot.id, displayName, request.level) ?: throw ApiException.notFound("Bot")
        bots.refresh()
        log.info("Admin: changed bot {}: level {}, name {}", updated.username, updated.botLevel, updated.displayName)
        return describe(listOf(updated)).single()
    }

    private suspend fun findBot(username: String): User =
        users.findByUsername(username)?.takeIf { it.isBot } ?: throw ApiException.notFound("Bot")

    private suspend fun describe(list: List<User>): List<AdminBot> {
        if (list.isEmpty()) return emptyList()
        val ids = list.map { it.id }
        val idSet = ids.toSet()
        val rows = ratings.forUsers(ids).groupBy { it.userId }
        val active = HashMap<Long, Int>()
        for (game in games.listActiveGamesOf(ids)) {
            for (seat in listOfNotNull(game.whiteId, game.blackId)) {
                if (seat in idSet) active.merge(seat, 1, Int::plus)
            }
        }
        val total = games.countGamesOf(ids)
        return list.map { bot ->
            AdminBot(
                user = UserRef.of(bot),
                ratings = PlayerRatings.of(rows[bot.id].orEmpty()),
                level = checkNotNull(bot.botLevel) { "Bot ${bot.username} has no level" },
                legacy = BotRoster.isLegacy(bot.username),
                activeGames = active[bot.id] ?: 0,
                totalGames = total[bot.id] ?: 0,
            )
        }
    }

    private companion object {
        /** How many of a bot's newest games its details show. */
        const val RECENT_GAMES = 20
    }
}
