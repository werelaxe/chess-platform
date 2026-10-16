package ru.werelaxe.chess.server.repository.exposed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import ru.werelaxe.chess.core.ChessJson
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.server.db.Games
import ru.werelaxe.chess.server.db.Moves
import ru.werelaxe.chess.server.db.RatingChanges
import ru.werelaxe.chess.server.db.Ratings
import ru.werelaxe.chess.server.model.GameFilter
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.model.GameRecord
import ru.werelaxe.chess.server.model.GameResult
import ru.werelaxe.chess.server.model.Rating
import ru.werelaxe.chess.server.model.RatingChange
import ru.werelaxe.chess.server.model.RatingUpdate
import ru.werelaxe.chess.server.model.SeatRatings
import ru.werelaxe.chess.server.model.Visibility
import ru.werelaxe.chess.server.repository.GameRepository
import java.time.Instant
import java.time.ZoneOffset

class ExposedGameRepository(private val db: Database) : GameRepository {
    override suspend fun create(game: GameRecord): GameRecord = tx {
        Games.insert {
            it[Games.id] = game.id
            it.fill(game)
        }
        game
    }

    override suspend fun findById(id: String): GameRecord? = tx {
        Games.selectAll().where { Games.id eq id }.singleOrNull()?.toRecord()
    }

    override suspend fun update(game: GameRecord) {
        tx { Games.update({ Games.id eq game.id }) { it.fill(game) } }
    }

    /** The moves and the rating changes go with the row (`ON DELETE CASCADE`). */
    override suspend fun delete(id: String) {
        tx { Games.deleteWhere { Games.id eq id } }
    }

    override suspend fun listAll(phase: GamePhase?, limit: Int, offset: Int): List<GameRecord> = tx {
        var query = Games.selectAll()
        if (phase != null) query = query.where { Games.status eq phase }
        query
            .orderBy(Games.createdAt to SortOrder.DESC, Games.id to SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toRecord() }
    }

    override suspend fun listPublic(filter: GameFilter?, kind: GameKind?, limit: Int, offset: Int): List<GameRecord> = tx {
        var condition: Op<Boolean> = isPublic()
        if (filter != null) condition = condition and (Games.status eq filter.phase)
        if (kind != null) condition = condition and (Games.kind eq kind)
        Games.selectAll()
            .where(condition)
            .orderBy(Games.createdAt to SortOrder.DESC, Games.id to SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toRecord() }
    }

    override suspend fun listForUser(userId: Long, limit: Int, offset: Int): List<GameRecord> = tx {
        Games.selectAll()
            .where { involves(userId) }
            .orderBy(Games.createdAt to SortOrder.DESC, Games.id to SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toRecord() }
    }

    override suspend fun listPublicForUser(userId: Long, limit: Int): List<GameRecord> = tx {
        Games.selectAll()
            .where { isPublic() and involves(userId) }
            .orderBy(Games.createdAt to SortOrder.DESC, Games.id to SortOrder.DESC)
            .limit(limit)
            .map { it.toRecord() }
    }

    override suspend fun moves(gameId: String): List<GameMove> = tx {
        Moves.selectAll()
            .where { Moves.gameId eq gameId }
            .orderBy(Moves.ply, SortOrder.ASC)
            .map { ChessJson.decodeMove(it[Moves.move]) }
    }

    override suspend fun recordMove(
        game: GameRecord,
        ply: Int,
        move: GameMove,
        createdAt: Instant,
        rating: RatingUpdate?,
    ): List<RatingChange> = tx {
        Moves.insert {
            it[Moves.gameId] = game.id
            it[Moves.ply] = ply
            it[Moves.move] = ChessJson.encodeMove(move)
            it[Moves.createdAt] = createdAt.atOffset(ZoneOffset.UTC)
        }
        Games.update({ Games.id eq game.id }) { it.fill(game) }
        rating?.let { applyRating(game, it) } ?: emptyList()
    }

    override suspend fun finish(game: GameRecord, rating: RatingUpdate?): List<RatingChange> = tx {
        Games.update({ Games.id eq game.id }) { it.fill(game) }
        rating?.let { applyRating(game, it) } ?: emptyList()
    }

    override suspend fun listIncomingInvitations(userId: Long): List<GameRecord> = tx {
        Games.selectAll()
            .where { (Games.inviteeId eq userId) and (Games.status eq GamePhase.WAITING) }
            .newestFirst()
    }

    override suspend fun listOutgoingInvitations(userId: Long): List<GameRecord> = tx {
        Games.selectAll()
            .where { (Games.creatorId eq userId) and Games.inviteeId.isNotNull() and (Games.status eq GamePhase.WAITING) }
            .newestFirst()
    }

    override suspend fun listInvitationsBy(creatorIds: Collection<Long>, createdSince: Instant?): List<GameRecord> {
        if (creatorIds.isEmpty()) return emptyList()
        return tx {
            var recent: Op<Boolean> = Games.status eq GamePhase.WAITING
            if (createdSince != null) recent = recent or (Games.createdAt greaterEq createdSince.atOffset(ZoneOffset.UTC))
            Games.selectAll()
                .where { Games.inviteeId.isNotNull() and (Games.creatorId inList creatorIds.toSet()) and recent }
                .newestFirst()
        }
    }

    override suspend fun countOpenGamesBy(creatorIds: Collection<Long>): Int {
        if (creatorIds.isEmpty()) return 0
        return tx {
            Games.selectAll().where { openToAnybody() and (Games.creatorId inList creatorIds.toSet()) }.count().toInt()
        }
    }

    override suspend fun listOpenGamesCreatedBefore(before: Instant, limit: Int): List<GameRecord> = tx {
        Games.selectAll()
            .where { openToAnybody() and (Games.createdAt lessEq before.atOffset(ZoneOffset.UTC)) }
            .oldestFirst(limit)
    }

    override suspend fun listOpenGamesCreatedBetween(after: Instant, before: Instant, limit: Int): List<GameRecord> = tx {
        Games.selectAll()
            .where {
                openToAnybody() and
                    (Games.createdAt greater after.atOffset(ZoneOffset.UTC)) and
                    (Games.createdAt lessEq before.atOffset(ZoneOffset.UTC))
            }
            .oldestFirst(limit)
    }

    override suspend fun listActiveGamesOf(playerIds: Collection<Long>): List<GameRecord> {
        if (playerIds.isEmpty()) return emptyList()
        val ids = playerIds.toSet()
        return tx {
            Games.selectAll()
                .where { (Games.status eq GamePhase.ACTIVE) and ((Games.whiteId inList ids) or (Games.blackId inList ids)) }
                .map { it.toRecord() }
        }
    }

    override suspend fun listActiveGamesLastMovedBefore(before: Instant, limit: Int): List<GameRecord> = tx {
        Games.selectAll()
            .where { (Games.status eq GamePhase.ACTIVE) and (Games.lastMoveAt less before.atOffset(ZoneOffset.UTC)) }
            .orderBy(Games.lastMoveAt to SortOrder.ASC, Games.id to SortOrder.ASC)
            .limit(limit)
            .map { it.toRecord() }
    }

    override suspend fun countActiveGamesAmong(playerIds: Collection<Long>): Int {
        if (playerIds.isEmpty()) return 0
        val ids = playerIds.toSet()
        return tx {
            Games.selectAll()
                .where { (Games.status eq GamePhase.ACTIVE) and (Games.whiteId inList ids) and (Games.blackId inList ids) }
                .count()
                .toInt()
        }
    }

    override suspend fun countActiveGamesWithHumans(botIds: Collection<Long>, updatedSince: Instant): Int {
        val ids = botIds.toSet()
        return tx {
            var condition: Op<Boolean> = (Games.status eq GamePhase.ACTIVE) and
                (Games.updatedAt greaterEq updatedSince.atOffset(ZoneOffset.UTC))
            // Both seats of an ACTIVE game are taken, so a seat outside the bot ids is a person's.
            if (ids.isNotEmpty()) condition = condition and ((Games.whiteId notInList ids) or (Games.blackId notInList ids))
            Games.selectAll().where(condition).count().toInt()
        }
    }

    override suspend fun countByPhase(): Map<GamePhase, Int> = tx {
        val count = Games.id.count()
        Games.select(Games.status, count).groupBy(Games.status).associate { it[Games.status] to it[count].toInt() }
    }

    override suspend fun countActiveByKind(): Map<GameKind, Int> = tx {
        val count = Games.id.count()
        Games.select(Games.kind, count)
            .where { Games.status eq GamePhase.ACTIVE }
            .groupBy(Games.kind)
            .associate { it[Games.kind] to it[count].toInt() }
    }

    override suspend fun countGamesOf(playerIds: Collection<Long>): Map<Long, Int> {
        if (playerIds.isEmpty()) return emptyMap()
        val ids = playerIds.toSet()
        return tx {
            val counts = HashMap<Long, Int>()
            val count = Games.id.count()
            for (seat in listOf(Games.whiteId, Games.blackId)) {
                Games.select(seat, count).where { seat inList ids }.groupBy(seat).forEach { row ->
                    row[seat]?.let { counts.merge(it, row[count].toInt(), Int::plus) }
                }
            }
            counts
        }
    }

    private fun openToAnybody(): Op<Boolean> =
        (Games.visibility eq Visibility.PUBLIC) and (Games.status eq GamePhase.WAITING) and Games.inviteeId.isNull()

    /** What the public listing shows: public games, except invitations that are still pending. */
    private fun isPublic(): Op<Boolean> =
        (Games.visibility eq Visibility.PUBLIC) and ((Games.status neq GamePhase.WAITING) or Games.inviteeId.isNull())

    private fun involves(userId: Long): Op<Boolean> =
        (Games.creatorId eq userId) or (Games.whiteId eq userId) or (Games.blackId eq userId)

    private fun org.jetbrains.exposed.v1.jdbc.Query.newestFirst(): List<GameRecord> =
        orderBy(Games.createdAt to SortOrder.DESC, Games.id to SortOrder.DESC).map { it.toRecord() }

    private fun org.jetbrains.exposed.v1.jdbc.Query.oldestFirst(limit: Int): List<GameRecord> =
        orderBy(Games.createdAt to SortOrder.ASC, Games.id to SortOrder.ASC).limit(limit).map { it.toRecord() }

    /**
     * Reads both players' rating rows (locked, so that two games of one player finishing at
     * the same time do not lose an update), stores the rows [update] derives from them and
     * records the changes. Call inside the transaction that stores the finished game.
     *
     * A row that does not exist yet cannot be locked, so the initial rows are inserted first
     * (`ON CONFLICT DO NOTHING`: a concurrent insert wins and is then read under the lock).
     */
    private fun JdbcTransaction.applyRating(game: GameRecord, update: RatingUpdate): List<RatingChange> {
        val whiteId = checkNotNull(game.whiteId) { "Game ${game.id} has no white player" }
        val blackId = checkNotNull(game.blackId) { "Game ${game.id} has no black player" }
        val at = game.finishedAt ?: game.updatedAt
        for (seat in listOf(whiteId, blackId)) {
            Ratings.insertIgnore { it.fill(Rating.initial(seat, game.kind, at)) }
        }
        val rows = Ratings.selectAll()
            .where { (Ratings.kind eq game.kind) and (Ratings.userId inList listOf(whiteId, blackId)) }
            .forUpdate(ForUpdateOption.ForUpdate)
            .associate { it[Ratings.userId] to it.toRating() }
        val before = SeatRatings(
            white = rows.getValue(whiteId),
            black = rows.getValue(blackId),
        )
        val after = update(before)
        return listOf(before.white to after.white, before.black to after.black).map { (old, new) ->
            Ratings.update({ (Ratings.userId eq new.userId) and (Ratings.kind eq new.kind) }) { it.fill(new) }
            RatingChanges.insert {
                it[RatingChanges.gameId] = game.id
                it[RatingChanges.userId] = new.userId
                it[RatingChanges.kind] = game.kind
                it[RatingChanges.ratingBefore] = old.rating
                it[RatingChanges.ratingAfter] = new.rating
                it[RatingChanges.createdAt] = at.atOffset(ZoneOffset.UTC)
            }
            RatingChange(game.id, new.userId, game.kind, old.rating, new.rating, at)
        }
    }

    private fun UpdateBuilder<*>.fill(game: GameRecord) {
        this[Games.kind] = game.kind
        this[Games.visibility] = game.visibility
        this[Games.status] = game.phase
        this[Games.creatorId] = game.creatorId
        this[Games.whiteId] = game.whiteId
        this[Games.blackId] = game.blackId
        this[Games.inviteeId] = game.inviteeId
        this[Games.rated] = game.rated
        this[Games.moveCount] = game.moveCount
        this[Games.resultWinner] = game.result?.winner
        this[Games.resultReason] = game.result?.reason
        this[Games.drawOfferedBy] = game.drawOfferedBy
        this[Games.whiteRatingBefore] = game.whiteRatingBefore
        this[Games.blackRatingBefore] = game.blackRatingBefore
        this[Games.createdAt] = game.createdAt.atOffset(ZoneOffset.UTC)
        this[Games.updatedAt] = game.updatedAt.atOffset(ZoneOffset.UTC)
        this[Games.lastMoveAt] = game.lastMoveAt?.atOffset(ZoneOffset.UTC)
        this[Games.finishedAt] = game.finishedAt?.atOffset(ZoneOffset.UTC)
    }

    private fun UpdateBuilder<*>.fill(rating: Rating) {
        this[Ratings.userId] = rating.userId
        this[Ratings.kind] = rating.kind
        this[Ratings.rating] = rating.rating
        this[Ratings.games] = rating.games
        this[Ratings.wins] = rating.wins
        this[Ratings.draws] = rating.draws
        this[Ratings.losses] = rating.losses
        this[Ratings.peak] = rating.peak
        this[Ratings.updatedAt] = rating.updatedAt.atOffset(ZoneOffset.UTC)
    }

    private fun ResultRow.toRecord(): GameRecord {
        val reason = this[Games.resultReason]
        return GameRecord(
            id = this[Games.id],
            kind = this[Games.kind],
            visibility = this[Games.visibility],
            phase = this[Games.status],
            creatorId = this[Games.creatorId],
            whiteId = this[Games.whiteId],
            blackId = this[Games.blackId],
            inviteeId = this[Games.inviteeId],
            rated = this[Games.rated],
            moveCount = this[Games.moveCount],
            result = reason?.let { GameResult(this[Games.resultWinner], it) },
            drawOfferedBy = this[Games.drawOfferedBy],
            whiteRatingBefore = this[Games.whiteRatingBefore],
            blackRatingBefore = this[Games.blackRatingBefore],
            createdAt = this[Games.createdAt].toInstant(),
            updatedAt = this[Games.updatedAt].toInstant(),
            lastMoveAt = this[Games.lastMoveAt]?.toInstant(),
            finishedAt = this[Games.finishedAt]?.toInstant(),
        )
    }

    private suspend fun <T> tx(block: JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) {
        transaction(db) { block() }
    }
}
