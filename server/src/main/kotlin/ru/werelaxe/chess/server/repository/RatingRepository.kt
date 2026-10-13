package ru.werelaxe.chess.server.repository

import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.server.model.RatedPlayer
import ru.werelaxe.chess.server.model.Rating
import ru.werelaxe.chess.server.model.RatingChange

/** Reads of the rating tables; the writes happen when a game finishes, in [GameRepository]. */
interface RatingRepository {
    /** The rating rows of [userIds] in every variant. */
    suspend fun forUsers(userIds: Collection<Long>): List<Rating>

    /** The changes recorded when [gameId] finished: one per seat, or none for an unrated result. */
    suspend fun changesFor(gameId: String): List<RatingChange>

    /** Players with a rated game in [kind], best first: by rating, then games, then username. */
    suspend fun leaderboard(kind: GameKind, limit: Int, offset: Int): List<RatedPlayer>
}
