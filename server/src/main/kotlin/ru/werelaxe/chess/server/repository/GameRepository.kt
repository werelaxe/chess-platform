package ru.werelaxe.chess.server.repository

import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.server.model.GameFilter
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.model.GameRecord
import ru.werelaxe.chess.server.model.RatingChange
import ru.werelaxe.chess.server.model.RatingUpdate
import java.time.Instant

interface GameRepository {
    suspend fun create(game: GameRecord): GameRecord

    suspend fun findById(id: String): GameRecord?

    /** Overwrites every column of the row identified by [game].id. */
    suspend fun update(game: GameRecord)

    /** Deletes the game with its moves and rating changes; the ratings they led to stay as they are. */
    suspend fun delete(id: String)

    /** Every game whatever its visibility, newest first (`created_at DESC, id DESC`); only those in [phase] when given. */
    suspend fun listAll(phase: GamePhase?, limit: Int, offset: Int): List<GameRecord>

    /** Public games, newest first (`created_at DESC, id DESC`); a WAITING invitation is not public until accepted. */
    suspend fun listPublic(filter: GameFilter?, kind: GameKind?, limit: Int, offset: Int): List<GameRecord>

    /** Games the user created or plays, newest first (`created_at DESC, id DESC`). */
    suspend fun listForUser(userId: Long, limit: Int, offset: Int): List<GameRecord>

    /** The games of [listForUser] that [listPublic] would show (public, not a pending invitation), newest first. */
    suspend fun listPublicForUser(userId: Long, limit: Int): List<GameRecord>

    /** The move list in ply order. */
    suspend fun moves(gameId: String): List<GameMove>

    /**
     * Atomically appends a move and stores the updated game row; when the move finished a rated
     * game, [rating] is applied in the same transaction and the recorded changes are returned.
     */
    suspend fun recordMove(
        game: GameRecord,
        ply: Int,
        move: GameMove,
        createdAt: Instant,
        rating: RatingUpdate? = null,
    ): List<RatingChange>

    /** Stores a game that just finished and, for a rated result, applies [rating] in the same transaction. */
    suspend fun finish(game: GameRecord, rating: RatingUpdate?): List<RatingChange>

    /** WAITING invitations sent to [userId], newest first. */
    suspend fun listIncomingInvitations(userId: Long): List<GameRecord>

    /** WAITING invitations created by [userId], newest first. */
    suspend fun listOutgoingInvitations(userId: Long): List<GameRecord>

    /**
     * Invitations created by any of [creatorIds] that are still WAITING, plus, when
     * [createdSince] is given, those of any status created since then; newest first.
     */
    suspend fun listInvitationsBy(creatorIds: Collection<Long>, createdSince: Instant?): List<GameRecord>

    /** Public games open to anybody (WAITING, no invitee) created by any of [creatorIds]. */
    suspend fun countOpenGamesBy(creatorIds: Collection<Long>): Int

    /** Public games open to anybody that were created at or before [before], oldest first. */
    suspend fun listOpenGamesCreatedBefore(before: Instant, limit: Int): List<GameRecord>

    /** Public games open to anybody created after [after] and at or before [before], oldest first. */
    suspend fun listOpenGamesCreatedBetween(after: Instant, before: Instant, limit: Int): List<GameRecord>

    /** ACTIVE games in which any of [playerIds] holds a seat. */
    suspend fun listActiveGamesOf(playerIds: Collection<Long>): List<GameRecord>

    /** How many ACTIVE games have both seats held by [playerIds]. */
    suspend fun countActiveGamesAmong(playerIds: Collection<Long>): Int

    /**
     * How many ACTIVE games have a seat that none of [botIds] holds: the games people (guests
     * included) are playing, against a bot or against each other.
     */
    suspend fun countActiveGamesWithHumans(botIds: Collection<Long>): Int

    /** How many games exist in each phase. */
    suspend fun countByPhase(): Map<GamePhase, Int>

    /** How many games each of [playerIds] holds a seat in, whatever the phase; players without any are left out. */
    suspend fun countGamesOf(playerIds: Collection<Long>): Map<Long, Int>
}
