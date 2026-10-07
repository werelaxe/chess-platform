package ru.werelaxe.chess.server.repository

import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.server.model.GameFilter
import ru.werelaxe.chess.server.model.GameRecord
import java.time.Instant

interface GameRepository {
    suspend fun create(game: GameRecord): GameRecord

    suspend fun findById(id: String): GameRecord?

    /** Overwrites every column of the row identified by [game].id. */
    suspend fun update(game: GameRecord)

    /** Deletes the game and its moves. */
    suspend fun delete(id: String)

    /** Public games, newest first (`created_at DESC, id DESC`). */
    suspend fun listPublic(filter: GameFilter?, kind: GameKind?, limit: Int, offset: Int): List<GameRecord>

    /** Games the user created or plays, newest first (`created_at DESC, id DESC`). */
    suspend fun listForUser(userId: Long, limit: Int, offset: Int): List<GameRecord>

    /** The move list in ply order. */
    suspend fun moves(gameId: String): List<GameMove>

    /** Atomically appends a move and stores the updated game row. */
    suspend fun recordMove(game: GameRecord, ply: Int, move: GameMove, createdAt: Instant)
}
