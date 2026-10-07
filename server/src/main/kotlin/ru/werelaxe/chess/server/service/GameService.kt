package ru.werelaxe.chess.server.service

import io.ktor.http.HttpStatusCode
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.Game
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.GameStatus
import ru.werelaxe.chess.server.dto.ColorChoice
import ru.werelaxe.chess.server.dto.CreateGameRequest
import ru.werelaxe.chess.server.dto.DrawAction
import ru.werelaxe.chess.server.dto.GameDto
import ru.werelaxe.chess.server.dto.GameSummary
import ru.werelaxe.chess.server.dto.MoveResponse
import ru.werelaxe.chess.server.dto.Opponent
import ru.werelaxe.chess.server.dto.UserRef
import ru.werelaxe.chess.server.model.GameFilter
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.model.GameRecord
import ru.werelaxe.chess.server.model.GameResult
import ru.werelaxe.chess.server.model.Visibility
import ru.werelaxe.chess.server.repository.GameRepository
import ru.werelaxe.chess.server.repository.UserRepository
import ru.werelaxe.chess.server.ws.GameEvents
import ru.werelaxe.chess.server.ws.ServerEvent
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * All game rules outside the chess rules themselves: lobby, turns, draw offers, resignation,
 * and the computer opponent's seat. [moveProvider] chooses the computer's moves; tests inject
 * a fast, seeded one. [botPause] is the shortest pause before the computer's reply to a move.
 * [cache] holds the replayed games; the caller owns it so that it can sweep it periodically.
 */
class GameService(
    private val games: GameRepository,
    private val users: UserRepository,
    private val events: GameEvents,
    private val random: Random,
    private val clock: Clock,
    moveProvider: MoveProvider,
    private val ids: GameIdGenerator = GameIdGenerator(),
    private val cache: GameCache = GameCache(),
    botPause: Duration = BotPlayer.DEFAULT_PAUSE,
) {
    private val bot = BotPlayer(this, moveProvider, botPause)

    /** Stops the computer player; call when the application shuts down. */
    fun close() = bot.close()

    suspend fun create(user: UserPrincipal, request: CreateGameRequest): GameDto {
        val color = when (request.color) {
            ColorChoice.WHITE -> Color.WHITE
            ColorChoice.BLACK -> Color.BLACK
            ColorChoice.RANDOM -> if (random.nextBoolean()) Color.WHITE else Color.BLACK
        }
        val now = clock.instant()
        val open = GameRecord(
            id = ids.next(),
            kind = request.kind,
            visibility = request.visibility,
            phase = GamePhase.WAITING,
            creatorId = user.id,
            whiteId = user.id.takeIf { color == Color.WHITE },
            blackId = user.id.takeIf { color == Color.BLACK },
            moveCount = 0,
            result = null,
            drawOfferedBy = null,
            botLevel = null,
            createdAt = now,
            updatedAt = now,
            finishedAt = null,
        )
        val record = when (request.opponent) {
            Opponent.HUMAN -> open
            Opponent.COMPUTER -> {
                val level = request.level ?: throw ApiException.validation(
                    "A level (EASY, MEDIUM or HARD) is required to play against the computer",
                )
                val computer = users.findByUsername(BotUser.USERNAME)?.takeIf { it.isBot }
                    ?: throw IllegalStateException("The '${BotUser.USERNAME}' account is missing")
                // The computer takes the other seat at once; the game is unlisted because there
                // is nothing to join and nothing in it for the lobby.
                open.copy(
                    visibility = Visibility.PRIVATE,
                    phase = GamePhase.ACTIVE,
                    whiteId = open.whiteId ?: computer.id,
                    blackId = open.blackId ?: computer.id,
                    botLevel = level,
                )
            }
        }
        games.create(record)
        bot.wake(record)
        return toDto(record, emptyList())
    }

    suspend fun listPublic(filter: GameFilter?, kind: GameKind?, limit: Int, offset: Int): List<GameSummary> =
        toSummaries(games.listPublic(filter, kind, limit, offset))

    suspend fun listMine(user: UserPrincipal, limit: Int, offset: Int): List<GameSummary> =
        toSummaries(games.listForUser(user.id, limit, offset))

    /** Reads the row and the moves under the game's lock so that they cannot straddle a move. */
    suspend fun get(id: String): GameDto = cache.locked(id) {
        val record = games.findById(id) ?: throw ApiException.notFound("Game")
        // A computer move lost to a restart is made as soon as somebody looks at the game.
        bot.wake(record)
        toDto(record, games.moves(id))
    }

    suspend fun join(user: UserPrincipal, id: String): GameDto = cache.locked(id) {
        val record = games.findById(id) ?: throw ApiException.notFound("Game")
        if (record.creatorId == user.id) {
            throw ApiException(HttpStatusCode.BadRequest, "own_game", "You cannot join your own game")
        }
        if (record.phase != GamePhase.WAITING) {
            throw ApiException(HttpStatusCode.Conflict, "game_full", "The game is not open for joining")
        }
        val joined = if (record.whiteId == null) record.copy(whiteId = user.id) else record.copy(blackId = user.id)
        val updated = joined.copy(phase = GamePhase.ACTIVE, updatedAt = clock.instant())
        games.update(updated)
        val dto = toDto(updated, emptyList())
        events.publish(id, ServerEvent.GameUpdated(dto))
        dto
    }

    suspend fun move(user: UserPrincipal, id: String, proposed: GameMove): MoveResponse = cache.locked(id) { entry ->
        val record = games.findById(id) ?: throw ApiException.notFound("Game")
        requireActive(record)
        val color = record.colorOf(user.id)
            ?: throw ApiException(HttpStatusCode.Forbidden, "not_a_player", "You are not a player of this game")
        val game = entry.game ?: Game.replay(record.kind, games.moves(id)).also { entry.game = it }
        if (game.sideToMove != color) {
            throw ApiException(HttpStatusCode.Forbidden, "not_your_turn", "It is not your turn")
        }
        val move = game.resolve(proposed, random)
        if (!game.isLegal(move)) {
            throw ApiException(HttpStatusCode.BadRequest, "illegal_move", "Illegal move")
        }
        val ply = game.moveCount
        game.apply(move)
        val status = game.status()
        val now = clock.instant()
        // A move by the opponent of the offerer answers the offer; the offerer's own move keeps it pending.
        var updated = record.copy(
            moveCount = ply + 1,
            updatedAt = now,
            drawOfferedBy = record.drawOfferedBy.takeIf { it == color },
        )
        if (status is GameStatus.Finished) {
            updated = updated.copy(
                phase = GamePhase.FINISHED,
                result = GameResult(status.winner, status.reason),
                drawOfferedBy = null,
                finishedAt = now,
            )
        }
        try {
            games.recordMove(updated, ply, move, now)
        } catch (e: Exception) {
            // The cached game is ahead of the database now; drop it so it is replayed next time.
            entry.game = null
            throw e
        }
        events.publish(id, ServerEvent.Move(ply, move, status))
        // The `game` event only carries what the `move` event does not: a changed phase or draw offer.
        if (updated.phase != record.phase || updated.drawOfferedBy != record.drawOfferedBy) {
            if (updated.phase == GamePhase.FINISHED) entry.game = null
            events.publish(id, ServerEvent.GameUpdated(toDto(updated, game.history.toList())))
        }
        bot.wake(updated, movedAt = TimeSource.Monotonic.markNow())
        MoveResponse(ply, move, status)
    }

    /**
     * A snapshot of a game against the computer in which the computer is on turn, or null when
     * it is not (the game is between people, is over, or the human is on turn). The move list
     * is a copy taken under the lock, so the search runs on it after the lock is released.
     */
    internal suspend fun botTurn(id: String): BotTurn? = cache.locked(id) { entry ->
        val record = games.findById(id) ?: return@locked null
        val level = record.botLevel ?: return@locked null
        val color = record.botColor ?: return@locked null
        if (record.phase != GamePhase.ACTIVE) return@locked null
        val game = entry.game ?: Game.replay(record.kind, games.moves(id)).also { entry.game = it }
        if (game.sideToMove != color) return@locked null
        val computerId = checkNotNull(if (color == Color.WHITE) record.whiteId else record.blackId)
        val computer = users.findById(computerId)
        check(computer != null && computer.isBot) { "The $color seat of game $id is not the computer" }
        BotTurn(id, record.kind, level, UserPrincipal(computer.id, computer.username, guest = false), game.history)
    }

    /**
     * Resigns an active game, or deletes a waiting game of which the caller is the creator; the
     * deleted game is reported (and published to its subscribers) as finished by abandonment.
     */
    suspend fun resign(user: UserPrincipal, id: String): GameDto = cache.locked(id) { entry ->
        val record = games.findById(id) ?: throw ApiException.notFound("Game")
        when (record.phase) {
            GamePhase.WAITING -> {
                if (record.creatorId != user.id) throw notAPlayer()
                games.delete(id)
                entry.game = null
                val now = clock.instant()
                val cancelled = record.copy(
                    phase = GamePhase.FINISHED,
                    result = GameResult(null, EndReason.ABANDONMENT),
                    updatedAt = now,
                    finishedAt = now,
                )
                val dto = toDto(cancelled, emptyList())
                events.publish(id, ServerEvent.GameUpdated(dto))
                dto
            }
            GamePhase.FINISHED -> throw gameFinished()
            GamePhase.ACTIVE -> {
                val color = record.colorOf(user.id) ?: throw notAPlayer()
                val now = clock.instant()
                val updated = record.copy(
                    phase = GamePhase.FINISHED,
                    result = GameResult(color.opposite, EndReason.RESIGNATION),
                    drawOfferedBy = null,
                    updatedAt = now,
                    finishedAt = now,
                )
                games.update(updated)
                entry.game = null
                val dto = toDto(updated, games.moves(id))
                events.publish(id, ServerEvent.GameUpdated(dto))
                dto
            }
        }
    }

    suspend fun draw(user: UserPrincipal, id: String, action: DrawAction): GameDto = cache.locked(id) { entry ->
        val record = games.findById(id) ?: throw ApiException.notFound("Game")
        requireActive(record)
        val color = record.colorOf(user.id) ?: throw notAPlayer()
        if (record.botLevel != null) {
            throw ApiException(
                HttpStatusCode.Conflict,
                "draw_not_available",
                "Draw offers are not available in games against the computer",
            )
        }
        val now = clock.instant()
        val updated = when (action) {
            DrawAction.OFFER -> {
                if (record.drawOfferedBy != null) {
                    throw ApiException(HttpStatusCode.Conflict, "draw_pending", "A draw offer is already pending")
                }
                record.copy(drawOfferedBy = color)
            }
            DrawAction.ACCEPT -> {
                if (record.drawOfferedBy != color.opposite) throw noDrawOffer("Your opponent has not offered a draw")
                record.copy(
                    phase = GamePhase.FINISHED,
                    result = GameResult(null, EndReason.DRAW_AGREEMENT),
                    drawOfferedBy = null,
                    finishedAt = now,
                )
            }
            DrawAction.DECLINE -> {
                if (record.drawOfferedBy != color.opposite) throw noDrawOffer("Your opponent has not offered a draw")
                record.copy(drawOfferedBy = null)
            }
            DrawAction.WITHDRAW -> {
                if (record.drawOfferedBy != color) throw noDrawOffer("You have no pending draw offer")
                record.copy(drawOfferedBy = null)
            }
        }.copy(updatedAt = now)
        games.update(updated)
        if (updated.phase == GamePhase.FINISHED) entry.game = null
        val dto = toDto(updated, games.moves(id))
        events.publish(id, ServerEvent.GameUpdated(dto))
        dto
    }

    private fun requireActive(record: GameRecord) {
        when (record.phase) {
            GamePhase.ACTIVE -> Unit
            GamePhase.FINISHED -> throw gameFinished()
            GamePhase.WAITING -> throw ApiException(
                HttpStatusCode.Conflict,
                "game_not_started",
                "The game is waiting for an opponent",
            )
        }
    }

    private fun notAPlayer() = ApiException(HttpStatusCode.Forbidden, "not_a_player", "You are not a player of this game")

    private fun gameFinished() = ApiException(HttpStatusCode.Conflict, "game_finished", "The game is already finished")

    private fun noDrawOffer(message: String) = ApiException(HttpStatusCode.Conflict, "no_draw_offer", message)

    private suspend fun toDto(record: GameRecord, moves: List<GameMove>): GameDto =
        toSummaries(listOf(record)).single().withMoves(moves)

    private suspend fun toSummaries(records: List<GameRecord>): List<GameSummary> {
        val userIds = records.flatMap { listOfNotNull(it.creatorId, it.whiteId, it.blackId) }.toSet()
        val usersById = users.findByIds(userIds)
        fun ref(id: Long?): UserRef? = id?.let { usersById[it] }?.let { UserRef.of(it) }
        return records.map { record ->
            GameSummary(
                id = record.id,
                kind = record.kind,
                visibility = record.visibility,
                status = record.phase,
                white = ref(record.whiteId),
                black = ref(record.blackId),
                creator = ref(record.creatorId) ?: UserRef(record.creatorId, "deleted"),
                moveCount = record.moveCount,
                result = record.result,
                drawOfferedBy = record.drawOfferedBy,
                botLevel = record.botLevel,
                createdAt = record.createdAt.toIso(),
                updatedAt = record.updatedAt.toIso(),
            )
        }
    }

    private fun Instant.toIso(): String = DateTimeFormatter.ISO_INSTANT.format(truncatedTo(ChronoUnit.MILLIS))
}
