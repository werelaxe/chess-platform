package ru.werelaxe.chess.server.repository.memory

import kotlinx.coroutines.sync.withLock
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.server.model.RatedPlayer
import ru.werelaxe.chess.server.model.Rating
import ru.werelaxe.chess.server.model.RatingChange
import ru.werelaxe.chess.server.repository.RatingRepository

/** Non-persistent repository for tests and local experiments. */
class InMemoryRatingRepository(private val store: InMemoryStore = InMemoryStore()) : RatingRepository {
    override suspend fun forUsers(userIds: Collection<Long>): List<Rating> = store.mutex.withLock {
        val ids = userIds.toSet()
        store.ratings.values.filter { it.userId in ids }
    }

    override suspend fun changesFor(gameId: String): List<RatingChange> = store.mutex.withLock {
        store.ratingChanges.filter { it.gameId == gameId }
    }

    override suspend fun leaderboard(kind: GameKind, limit: Int, offset: Int): List<RatedPlayer> = store.mutex.withLock {
        store.ratings.values
            .filter { it.kind == kind }
            .mapNotNull { rating -> store.users[rating.userId]?.let { RatedPlayer(it, rating) } }
            .sortedWith(
                compareByDescending<RatedPlayer> { it.rating.rating }
                    .thenByDescending { it.rating.games }
                    .thenBy { it.user.username.lowercase() },
            )
            .drop(offset)
            .take(limit)
    }
}
