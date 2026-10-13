package ru.werelaxe.chess.server.repository.exposed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.server.db.RatingChanges
import ru.werelaxe.chess.server.db.Ratings
import ru.werelaxe.chess.server.db.Users
import ru.werelaxe.chess.server.model.RatedPlayer
import ru.werelaxe.chess.server.model.Rating
import ru.werelaxe.chess.server.model.RatingChange
import ru.werelaxe.chess.server.repository.RatingRepository

class ExposedRatingRepository(private val db: Database) : RatingRepository {
    override suspend fun forUsers(userIds: Collection<Long>): List<Rating> {
        if (userIds.isEmpty()) return emptyList()
        return tx { Ratings.selectAll().where { Ratings.userId inList userIds.toSet() }.map { it.toRating() } }
    }

    override suspend fun changesFor(gameId: String): List<RatingChange> = tx {
        RatingChanges.selectAll()
            .where { RatingChanges.gameId eq gameId }
            .orderBy(RatingChanges.id to SortOrder.ASC)
            .map { it.toRatingChange() }
    }

    override suspend fun leaderboard(kind: GameKind, limit: Int, offset: Int): List<RatedPlayer> = tx {
        Ratings.innerJoin(Users)
            .selectAll()
            .where { Ratings.kind eq kind }
            .orderBy(Ratings.rating to SortOrder.DESC, Ratings.games to SortOrder.DESC, Users.usernameLower to SortOrder.ASC)
            .limit(limit)
            .offset(offset.toLong())
            .map { RatedPlayer(it.toUser(), it.toRating()) }
    }

    private suspend fun <T> tx(block: JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) {
        transaction(db) { block() }
    }
}
