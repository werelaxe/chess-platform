package ru.werelaxe.chess.server.repository.memory

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.server.model.GameFilter
import ru.werelaxe.chess.server.model.GameRecord
import ru.werelaxe.chess.server.model.Visibility
import ru.werelaxe.chess.server.repository.GameRepository
import java.time.Instant

/** Non-persistent repository for tests and local experiments. */
class InMemoryGameRepository : GameRepository {
    private val mutex = Mutex()
    private val games = LinkedHashMap<String, GameRecord>()
    private val moves = HashMap<String, MutableList<GameMove>>()

    override suspend fun create(game: GameRecord): GameRecord = mutex.withLock {
        require(game.id !in games) { "Duplicate game id ${game.id}" }
        games[game.id] = game
        moves[game.id] = ArrayList()
        game
    }

    override suspend fun findById(id: String): GameRecord? = mutex.withLock { games[id] }

    override suspend fun update(game: GameRecord) {
        mutex.withLock {
            require(game.id in games) { "Unknown game id ${game.id}" }
            games[game.id] = game
        }
    }

    override suspend fun delete(id: String) {
        mutex.withLock {
            games.remove(id)
            moves.remove(id)
        }
    }

    override suspend fun listPublic(filter: GameFilter?, kind: GameKind?, limit: Int, offset: Int): List<GameRecord> =
        mutex.withLock {
            games.values
                .filter { it.visibility == Visibility.PUBLIC }
                .filter { filter == null || it.phase == filter.phase }
                .filter { kind == null || it.kind == kind }
                .sortedNewestFirst()
                .drop(offset)
                .take(limit)
        }

    override suspend fun listForUser(userId: Long, limit: Int, offset: Int): List<GameRecord> = mutex.withLock {
        games.values.filter { it.involves(userId) }.sortedNewestFirst().drop(offset).take(limit)
    }

    override suspend fun moves(gameId: String): List<GameMove> = mutex.withLock {
        moves[gameId]?.toList() ?: emptyList()
    }

    override suspend fun recordMove(game: GameRecord, ply: Int, move: GameMove, createdAt: Instant) {
        mutex.withLock {
            val list = moves[game.id] ?: error("Unknown game id ${game.id}")
            check(list.size == ply) { "Expected ply ${list.size}, got $ply" }
            list.add(move)
            games[game.id] = game
        }
    }

    /** The same order as the SQL repository: `created_at DESC, id DESC`. */
    private fun List<GameRecord>.sortedNewestFirst(): List<GameRecord> =
        sortedWith(compareByDescending<GameRecord> { it.createdAt }.thenByDescending { it.id })
}
