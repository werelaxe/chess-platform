package ru.werelaxe.chess.server.repository.memory

import kotlinx.coroutines.sync.withLock
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.server.model.GameFilter
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.model.GameRecord
import ru.werelaxe.chess.server.model.RatingChange
import ru.werelaxe.chess.server.model.RatingUpdate
import ru.werelaxe.chess.server.model.Visibility
import ru.werelaxe.chess.server.repository.GameRepository
import java.time.Instant

/** Non-persistent repository for tests and local experiments. */
class InMemoryGameRepository(private val store: InMemoryStore = InMemoryStore()) : GameRepository {
    private val games get() = store.games

    override suspend fun create(game: GameRecord): GameRecord = store.mutex.withLock {
        require(game.id !in games) { "Duplicate game id ${game.id}" }
        games[game.id] = game
        store.moves[game.id] = ArrayList()
        game
    }

    override suspend fun findById(id: String): GameRecord? = store.mutex.withLock { games[id] }

    override suspend fun update(game: GameRecord) {
        store.mutex.withLock {
            require(game.id in games) { "Unknown game id ${game.id}" }
            games[game.id] = game
        }
    }

    override suspend fun delete(id: String) {
        store.mutex.withLock {
            games.remove(id)
            store.moves.remove(id)
            store.ratingChanges.removeIf { it.gameId == id }
        }
    }

    override suspend fun listAll(phase: GamePhase?, limit: Int, offset: Int): List<GameRecord> = store.mutex.withLock {
        games.values.filter { phase == null || it.phase == phase }.sortedNewestFirst().drop(offset).take(limit)
    }

    override suspend fun listPublic(filter: GameFilter?, kind: GameKind?, limit: Int, offset: Int): List<GameRecord> =
        store.mutex.withLock {
            games.values
                .filter { it.isPublic }
                .filter { filter == null || it.phase == filter.phase }
                .filter { kind == null || it.kind == kind }
                .sortedNewestFirst()
                .drop(offset)
                .take(limit)
        }

    override suspend fun listForUser(userId: Long, limit: Int, offset: Int): List<GameRecord> = store.mutex.withLock {
        games.values.filter { it.involves(userId) }.sortedNewestFirst().drop(offset).take(limit)
    }

    override suspend fun listPublicForUser(userId: Long, limit: Int): List<GameRecord> = store.mutex.withLock {
        games.values.filter { it.isPublic && it.involves(userId) }.sortedNewestFirst().take(limit)
    }

    override suspend fun moves(gameId: String): List<GameMove> = store.mutex.withLock {
        store.moves[gameId]?.toList() ?: emptyList()
    }

    override suspend fun recordMove(
        game: GameRecord,
        ply: Int,
        move: GameMove,
        createdAt: Instant,
        rating: RatingUpdate?,
    ): List<RatingChange> = store.mutex.withLock {
        val list = store.moves[game.id] ?: error("Unknown game id ${game.id}")
        check(list.size == ply) { "Expected ply ${list.size}, got $ply" }
        list.add(move)
        games[game.id] = game
        rating?.let { store.applyRating(game, it) } ?: emptyList()
    }

    override suspend fun finish(game: GameRecord, rating: RatingUpdate?): List<RatingChange> = store.mutex.withLock {
        require(game.id in games) { "Unknown game id ${game.id}" }
        games[game.id] = game
        rating?.let { store.applyRating(game, it) } ?: emptyList()
    }

    override suspend fun listIncomingInvitations(userId: Long): List<GameRecord> = store.mutex.withLock {
        games.values.filter { it.phase == GamePhase.WAITING && it.inviteeId == userId }.sortedNewestFirst()
    }

    override suspend fun listOutgoingInvitations(userId: Long): List<GameRecord> = store.mutex.withLock {
        games.values.filter { it.phase == GamePhase.WAITING && it.isInvitation && it.creatorId == userId }.sortedNewestFirst()
    }

    override suspend fun listInvitationsBy(creatorIds: Collection<Long>, createdSince: Instant?): List<GameRecord> =
        store.mutex.withLock {
            val creators = creatorIds.toSet()
            games.values
                .filter { it.isInvitation && it.creatorId in creators }
                .filter { it.phase == GamePhase.WAITING || (createdSince != null && it.createdAt >= createdSince) }
                .sortedNewestFirst()
        }

    override suspend fun countOpenGamesBy(creatorIds: Collection<Long>): Int = store.mutex.withLock {
        val creators = creatorIds.toSet()
        games.values.count { it.isOpenToAnybody && it.creatorId in creators }
    }

    override suspend fun listOpenGamesCreatedBefore(before: Instant, limit: Int): List<GameRecord> = store.mutex.withLock {
        games.values.filter { it.isOpenToAnybody && it.createdAt <= before }.sortedOldestFirst().take(limit)
    }

    override suspend fun listOpenGamesCreatedBetween(after: Instant, before: Instant, limit: Int): List<GameRecord> =
        store.mutex.withLock {
            games.values
                .filter { it.isOpenToAnybody && it.createdAt > after && it.createdAt <= before }
                .sortedOldestFirst()
                .take(limit)
        }

    override suspend fun listActiveGamesOf(playerIds: Collection<Long>): List<GameRecord> = store.mutex.withLock {
        val players = playerIds.toSet()
        games.values.filter { it.phase == GamePhase.ACTIVE && (it.whiteId in players || it.blackId in players) }
    }

    override suspend fun listActiveGamesUpdatedBefore(before: Instant, limit: Int): List<GameRecord> = store.mutex.withLock {
        games.values
            .filter { it.phase == GamePhase.ACTIVE && it.updatedAt < before }
            .sortedWith(compareBy<GameRecord> { it.updatedAt }.thenBy { it.id })
            .take(limit)
    }

    override suspend fun countActiveGamesAmong(playerIds: Collection<Long>): Int = store.mutex.withLock {
        val players = playerIds.toSet()
        games.values.count { it.phase == GamePhase.ACTIVE && it.whiteId in players && it.blackId in players }
    }

    override suspend fun countActiveGamesWithHumans(botIds: Collection<Long>, updatedSince: Instant): Int =
        store.mutex.withLock {
            val bots = botIds.toSet()
            games.values.count {
                it.phase == GamePhase.ACTIVE && it.updatedAt >= updatedSince && (it.whiteId !in bots || it.blackId !in bots)
            }
        }

    override suspend fun countByPhase(): Map<GamePhase, Int> = store.mutex.withLock {
        games.values.groupingBy { it.phase }.eachCount()
    }

    override suspend fun countGamesOf(playerIds: Collection<Long>): Map<Long, Int> = store.mutex.withLock {
        val players = playerIds.toSet()
        val counts = HashMap<Long, Int>()
        for (game in games.values) {
            for (seat in listOfNotNull(game.whiteId, game.blackId)) {
                if (seat in players) counts.merge(seat, 1, Int::plus)
            }
        }
        counts
    }

    private val GameRecord.isOpenToAnybody: Boolean
        get() = visibility == Visibility.PUBLIC && phase == GamePhase.WAITING && !isInvitation

    /** What the public listing shows: public games, except invitations that are still pending. */
    private val GameRecord.isPublic: Boolean
        get() = visibility == Visibility.PUBLIC && !(phase == GamePhase.WAITING && isInvitation)

    /** The same order as the SQL repository: `created_at DESC, id DESC`. */
    private fun List<GameRecord>.sortedNewestFirst(): List<GameRecord> =
        sortedWith(compareByDescending<GameRecord> { it.createdAt }.thenByDescending { it.id })

    /** The same order as the SQL repository: `created_at ASC, id ASC`. */
    private fun List<GameRecord>.sortedOldestFirst(): List<GameRecord> =
        sortedWith(compareBy<GameRecord> { it.createdAt }.thenBy { it.id })
}
