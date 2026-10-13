package ru.werelaxe.chess.server.repository.memory

import kotlinx.coroutines.sync.Mutex
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.server.model.GameRecord
import ru.werelaxe.chess.server.model.Rating
import ru.werelaxe.chess.server.model.RatingChange
import ru.werelaxe.chess.server.model.RatingUpdate
import ru.werelaxe.chess.server.model.SeatRatings
import ru.werelaxe.chess.server.model.User
import ru.werelaxe.chess.server.repository.Repositories

/**
 * The tables behind the in-memory repositories and the one lock that guards them, so that a
 * game and its rating changes are written together like in one database transaction.
 */
class InMemoryStore {
    val mutex = Mutex()
    val users = LinkedHashMap<Long, User>()
    val userIdByLowerName = HashMap<String, Long>()
    var nextUserId = 1L
    val games = LinkedHashMap<String, GameRecord>()
    val moves = HashMap<String, MutableList<GameMove>>()
    val ratings = HashMap<Pair<Long, GameKind>, Rating>()
    val ratingChanges = ArrayList<RatingChange>()

    /** The in-memory repositories over this store. */
    fun repositories() = Repositories(
        users = InMemoryUserRepository(this),
        games = InMemoryGameRepository(this),
        ratings = InMemoryRatingRepository(this),
    )

    /**
     * Applies [update] to both seats of the finished [game] and records the changes; call under
     * [mutex]. Like the SQL repository, a missing row is created as the initial one and then read.
     */
    fun applyRating(game: GameRecord, update: RatingUpdate): List<RatingChange> {
        val whiteId = checkNotNull(game.whiteId) { "Game ${game.id} has no white player" }
        val blackId = checkNotNull(game.blackId) { "Game ${game.id} has no black player" }
        val at = game.finishedAt ?: game.updatedAt
        val before = SeatRatings(
            white = ratings.getOrPut(whiteId to game.kind) { Rating.initial(whiteId, game.kind, at) },
            black = ratings.getOrPut(blackId to game.kind) { Rating.initial(blackId, game.kind, at) },
        )
        val after = update(before)
        return listOf(before.white to after.white, before.black to after.black).map { (old, new) ->
            ratings[new.userId to new.kind] = new
            RatingChange(game.id, new.userId, game.kind, old.rating, new.rating, at).also { ratingChanges += it }
        }
    }
}
