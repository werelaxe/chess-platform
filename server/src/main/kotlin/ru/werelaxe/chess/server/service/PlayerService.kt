package ru.werelaxe.chess.server.service

import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.server.dto.LeaderboardEntry
import ru.werelaxe.chess.server.dto.PlayerProfile
import ru.werelaxe.chess.server.dto.PlayerRatings
import ru.werelaxe.chess.server.dto.PlayerSummary
import ru.werelaxe.chess.server.dto.UserRef
import ru.werelaxe.chess.server.model.User
import ru.werelaxe.chess.server.repository.GameRepository
import ru.werelaxe.chess.server.repository.RatingRepository
import ru.werelaxe.chess.server.repository.UserRepository

/** Players as others see them: search, leaderboard, profiles and the bot roster (docs/ARCHITECTURE.md section 2.9). */
class PlayerService(
    private val users: UserRepository,
    private val games: GameRepository,
    private val ratings: RatingRepository,
    private val bots: Bots,
    private val gameService: GameService,
) {
    suspend fun search(query: String, limit: Int, offset: Int): List<PlayerSummary> =
        summaries(users.search(query.trim(), limit, offset))

    suspend fun leaderboard(kind: GameKind, limit: Int, offset: Int): List<LeaderboardEntry> {
        val ranked = ratings.leaderboard(kind, limit, offset)
        val rows = ratings.forUsers(ranked.map { it.user.id }).groupBy { it.userId }
        return ranked.mapIndexed { index, player ->
            LeaderboardEntry(
                user = UserRef.of(player.user, player.rating.rating),
                ratings = PlayerRatings.of(rows[player.user.id].orEmpty()),
                rank = offset + index + 1,
                rating = player.rating.rating,
            )
        }
    }

    /**
     * Guests have no profile: they are reported as unknown. The profile is public, so the recent
     * games are only those the public listing shows: private games and pending invitations stay
     * between their players.
     */
    suspend fun profile(username: String): PlayerProfile {
        val user = users.findByUsername(username)?.takeIf { !it.isGuest } ?: throw ApiException.notFound("Player")
        return PlayerProfile(
            user = UserRef.of(user),
            ratings = PlayerRatings.of(ratings.forUsers(listOf(user.id))),
            memberSince = user.createdAt.toIso(),
            lastSeen = user.lastSeenAt?.takeUnless { user.isBot }?.toIso(),
            recentGames = gameService.toSummaries(games.listPublicForUser(user.id, RECENT_GAMES)),
        )
    }

    /** The roster offered to players, in roster order. */
    suspend fun bots(): List<PlayerSummary> = summaries(bots.roster)

    private suspend fun summaries(players: List<User>): List<PlayerSummary> {
        val rows = ratings.forUsers(players.map { it.id }).groupBy { it.userId }
        return players.map { PlayerSummary(UserRef.of(it), PlayerRatings.of(rows[it.id].orEmpty())) }
    }

    private companion object {
        const val RECENT_GAMES = 10
    }
}
