package ru.werelaxe.chess.server.repository.exposed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import ru.werelaxe.chess.core.ChessJson
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.server.db.Games
import ru.werelaxe.chess.server.db.Moves
import ru.werelaxe.chess.server.model.GameFilter
import ru.werelaxe.chess.server.model.GameRecord
import ru.werelaxe.chess.server.model.GameResult
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

    override suspend fun delete(id: String) {
        tx { Games.deleteWhere { Games.id eq id } }
    }

    override suspend fun listPublic(filter: GameFilter?, kind: GameKind?, limit: Int, offset: Int): List<GameRecord> = tx {
        var condition: Op<Boolean> = Games.visibility eq Visibility.PUBLIC
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
            .where { (Games.creatorId eq userId) or (Games.whiteId eq userId) or (Games.blackId eq userId) }
            .orderBy(Games.createdAt to SortOrder.DESC, Games.id to SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toRecord() }
    }

    override suspend fun moves(gameId: String): List<GameMove> = tx {
        Moves.selectAll()
            .where { Moves.gameId eq gameId }
            .orderBy(Moves.ply, SortOrder.ASC)
            .map { ChessJson.decodeMove(it[Moves.move]) }
    }

    override suspend fun recordMove(game: GameRecord, ply: Int, move: GameMove, createdAt: Instant) {
        tx {
            Moves.insert {
                it[Moves.gameId] = game.id
                it[Moves.ply] = ply
                it[Moves.move] = ChessJson.encodeMove(move)
                it[Moves.createdAt] = createdAt.atOffset(ZoneOffset.UTC)
            }
            Games.update({ Games.id eq game.id }) { it.fill(game) }
        }
    }

    private fun UpdateBuilder<*>.fill(game: GameRecord) {
        this[Games.kind] = game.kind
        this[Games.visibility] = game.visibility
        this[Games.status] = game.phase
        this[Games.creatorId] = game.creatorId
        this[Games.whiteId] = game.whiteId
        this[Games.blackId] = game.blackId
        this[Games.moveCount] = game.moveCount
        this[Games.resultWinner] = game.result?.winner
        this[Games.resultReason] = game.result?.reason
        this[Games.drawOfferedBy] = game.drawOfferedBy
        this[Games.botLevel] = game.botLevel
        this[Games.createdAt] = game.createdAt.atOffset(ZoneOffset.UTC)
        this[Games.updatedAt] = game.updatedAt.atOffset(ZoneOffset.UTC)
        this[Games.finishedAt] = game.finishedAt?.atOffset(ZoneOffset.UTC)
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
            moveCount = this[Games.moveCount],
            result = reason?.let { GameResult(this[Games.resultWinner], it) },
            drawOfferedBy = this[Games.drawOfferedBy],
            botLevel = this[Games.botLevel],
            createdAt = this[Games.createdAt].toInstant(),
            updatedAt = this[Games.updatedAt].toInstant(),
            finishedAt = this[Games.finishedAt]?.toInstant(),
        )
    }

    private suspend fun <T> tx(block: JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) {
        transaction(db) { block() }
    }
}
