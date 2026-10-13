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
import ru.werelaxe.chess.server.dto.GameRatingChanges
import ru.werelaxe.chess.server.dto.GameSummary
import ru.werelaxe.chess.server.dto.InvitationsResponse
import ru.werelaxe.chess.server.dto.MoveResponse
import ru.werelaxe.chess.server.dto.Opponent
import ru.werelaxe.chess.server.dto.RatingDelta
import ru.werelaxe.chess.server.dto.UserRef
import ru.werelaxe.chess.server.model.GameFilter
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.model.GameRecord
import ru.werelaxe.chess.server.model.GameResult
import ru.werelaxe.chess.server.model.RatingChange
import ru.werelaxe.chess.server.model.RatingUpdate
import ru.werelaxe.chess.server.model.User
import ru.werelaxe.chess.server.model.Visibility
import ru.werelaxe.chess.server.repository.GameRepository
import ru.werelaxe.chess.server.repository.RatingRepository
import ru.werelaxe.chess.server.repository.UserRepository
import ru.werelaxe.chess.server.ws.GameEvents
import ru.werelaxe.chess.server.ws.ServerEvent
import java.time.Clock
import java.time.Instant
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * All game rules outside the chess rules themselves: lobby, invitations, turns, draw offers,
 * resignation, ratings and the bots' seats. [moveProvider] chooses the bots' moves; tests
 * inject a fast, seeded one. [settings] hold the pauses the bot player keeps before its moves.
 * [cache] holds the replayed games; the caller owns it so that it can sweep it periodically.
 */
class GameService(
    private val games: GameRepository,
    private val users: UserRepository,
    private val ratings: RatingRepository,
    private val bots: Bots,
    private val events: GameEvents,
    private val random: Random,
    private val clock: Clock,
    moveProvider: MoveProvider,
    settings: Settings,
    private val ratingService: RatingService = RatingService(),
    private val ids: GameIdGenerator = GameIdGenerator(),
    private val cache: GameCache = GameCache(),
) {
    private val bot = BotPlayer(this, bots, moveProvider, settings)

    /** Stops the bot player; call when the application shuts down. */
    fun close() = bot.close()

    /**
     * Wakes the bot in every ACTIVE game in which a bot is on turn: a move the previous instance
     * did not get to make, or a search that failed, is retried this way at startup and on every
     * matchmaking tick instead of waiting for somebody to look at the game. Returns how many
     * games were woken.
     */
    suspend fun wakeBots(): Int {
        var woken = 0
        for (record in games.listActiveGamesOf(bots.ids)) {
            if (!bot.isBotsTurn(record)) continue
            bot.wake(record)
            woken++
        }
        return woken
    }

    /**
     * Creates a game: open to anybody, or an invitation to `opponentUsername`. A bot invitee
     * accepts at once, so the game comes back ACTIVE. A guest's game is never rated.
     */
    suspend fun create(user: UserPrincipal, request: CreateGameRequest): GameDto {
        val color = when (request.color) {
            ColorChoice.WHITE -> Color.WHITE
            ColorChoice.BLACK -> Color.BLACK
            ColorChoice.RANDOM -> if (random.nextBoolean()) Color.WHITE else Color.BLACK
        }
        val invitee = when (request.opponent) {
            Opponent.OPEN -> null
            Opponent.USER -> findInvitee(user, request.opponentUsername)
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
            inviteeId = invitee?.id,
            rated = request.rated && !user.guest,
            moveCount = 0,
            result = null,
            drawOfferedBy = null,
            whiteRatingBefore = null,
            blackRatingBefore = null,
            createdAt = now,
            updatedAt = now,
            finishedAt = null,
        )
        val record = if (invitee != null && invitee.isBot) activate(open, invitee, now) else open
        games.create(record)
        bot.wake(record)
        return toDto(record, emptyList())
    }

    private suspend fun findInvitee(creator: UserPrincipal, username: String?): User {
        val name = username?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ApiException.validation("opponentUsername is required to invite a player")
        // Guests cannot be found by name: they exist only through their own token.
        val invitee = users.findByUsername(name)?.takeIf { !it.isGuest } ?: throw ApiException.notFound("Player")
        if (invitee.id == creator.id) throw ApiException.validation("You cannot invite yourself")
        return invitee
    }

    /**
     * Seats [joiner] in the free seat and starts the game: the rated flag drops for a guest and
     * both players' ratings in the game's kind are snapshotted.
     */
    private suspend fun activate(record: GameRecord, joiner: User, now: Instant): GameRecord {
        val seated = if (record.whiteId == null) record.copy(whiteId = joiner.id) else record.copy(blackId = joiner.id)
        val whiteId = checkNotNull(seated.whiteId)
        val blackId = checkNotNull(seated.blackId)
        val players = users.findByIds(listOf(whiteId, blackId))
        val rows = ratings.forUsers(players.keys)
        fun snapshot(id: Long): Int? = players[id]?.let { currentRating(it, rows, record.kind) }
        return seated.copy(
            phase = GamePhase.ACTIVE,
            rated = record.rated && !joiner.isGuest,
            whiteRatingBefore = snapshot(whiteId),
            blackRatingBefore = snapshot(blackId),
            updatedAt = now,
        )
    }

    suspend fun listPublic(filter: GameFilter?, kind: GameKind?, limit: Int, offset: Int): List<GameSummary> =
        toSummaries(games.listPublic(filter, kind, limit, offset))

    suspend fun listMine(user: UserPrincipal, limit: Int, offset: Int): List<GameSummary> =
        toSummaries(games.listForUser(user.id, limit, offset))

    /** The caller's pending invitations, received and sent. */
    suspend fun listInvitations(user: UserPrincipal): InvitationsResponse = InvitationsResponse(
        incoming = toSummaries(games.listIncomingInvitations(user.id)),
        outgoing = toSummaries(games.listOutgoingInvitations(user.id)),
    )

    /** Reads the row and the moves under the game's lock so that they cannot straddle a move. */
    suspend fun get(id: String): GameDto = cache.locked(id) {
        val record = games.findById(id) ?: throw ApiException.notFound("Game")
        // A bot move lost to a restart is made as soon as somebody looks at the game.
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
        if (record.inviteeId != null && record.inviteeId != user.id) {
            throw ApiException(HttpStatusCode.Forbidden, "not_invited", "This game is an invitation for another player")
        }
        val joiner = users.findById(user.id) ?: throw ApiException.unauthorized("Unknown user")
        val updated = activate(record, joiner, clock.instant())
        games.update(updated)
        val dto = toDto(updated, emptyList())
        events.publish(id, ServerEvent.GameUpdated(dto))
        bot.wake(updated)
        dto
    }

    /**
     * The invitee turns the invitation down: the game is deleted and its subscribers see it
     * abandoned, with `declined` set so that the creator can tell it from their own cancellation.
     */
    suspend fun decline(user: UserPrincipal, id: String) {
        cache.locked(id) { entry ->
            val record = games.findById(id) ?: throw ApiException.notFound("Game")
            if (record.phase != GamePhase.WAITING) {
                throw ApiException(HttpStatusCode.Conflict, "game_full", "The invitation is no longer pending")
            }
            if (record.inviteeId != user.id) {
                throw ApiException(HttpStatusCode.Forbidden, "not_invited", "You are not the invitee of this game")
            }
            cancel(record, entry, declined = true)
        }
    }

    /**
     * The creator withdraws a pending invitation. Unlike [resign], this never touches a game
     * that has started: once the invitee accepted it, the answer is 409 `game_full`, so a
     * caller that decided to cancel on a stale listing cannot resign the game by mistake.
     */
    suspend fun cancelInvitation(user: UserPrincipal, id: String): GameDto = cache.locked(id) { entry ->
        val record = games.findById(id) ?: throw ApiException.notFound("Game")
        if (record.phase != GamePhase.WAITING || record.inviteeId == null) {
            throw ApiException(HttpStatusCode.Conflict, "game_full", "The invitation is no longer pending")
        }
        if (record.creatorId != user.id) throw notAPlayer()
        cancel(record, entry)
    }

    /**
     * Deletes a public open game nobody has joined that was created at or before [createdBefore],
     * reporting it as abandoned like a cancellation; false when the game is gone or no longer
     * matches (somebody joined it meanwhile), which is checked again under the game's lock.
     */
    suspend fun expireOpenGame(id: String, createdBefore: Instant): Boolean = cache.locked(id) { entry ->
        val record = games.findById(id) ?: return@locked false
        val stale = record.phase == GamePhase.WAITING && record.inviteeId == null &&
            record.visibility == Visibility.PUBLIC && !record.createdAt.isAfter(createdBefore)
        if (stale) cancel(record, entry)
        stale
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
        var rating: RatingUpdate? = null
        if (status is GameStatus.Finished) {
            updated = updated.copy(
                phase = GamePhase.FINISHED,
                result = GameResult(status.winner, status.reason),
                drawOfferedBy = null,
                finishedAt = now,
            )
            rating = ratingUpdate(updated, status.winner, now)
        }
        val changes = try {
            games.recordMove(updated, ply, move, now, rating)
        } catch (e: Exception) {
            // The cached game is ahead of the database now; drop it so it is replayed next time.
            entry.game = null
            throw e
        }
        events.publish(id, ServerEvent.Move(ply, move, status))
        // The `game` event only carries what the `move` event does not: a changed phase or draw offer.
        if (updated.phase != record.phase || updated.drawOfferedBy != record.drawOfferedBy) {
            if (updated.phase == GamePhase.FINISHED) entry.game = null
            events.publish(id, ServerEvent.GameUpdated(toDto(updated, game.history.toList(), changes)))
        }
        bot.wake(updated, movedAt = TimeSource.Monotonic.markNow())
        MoveResponse(ply, move, status)
    }

    /**
     * A snapshot of a game in which a bot is on turn, or null when none is (the game is between
     * people, is over, or a person is on turn). The move list is a copy taken under the lock,
     * so the search runs on it after the lock is released.
     */
    internal suspend fun botTurn(id: String): BotTurn? = cache.locked(id) { entry ->
        val record = games.findById(id) ?: return@locked null
        if (record.phase != GamePhase.ACTIVE) return@locked null
        val color = record.sideToMove
        val player = bots[record.seat(color) ?: return@locked null] ?: return@locked null
        val game = entry.game ?: Game.replay(record.kind, games.moves(id)).also { entry.game = it }
        if (game.sideToMove != color) return@locked null
        val level = checkNotNull(player.botLevel) { "Bot ${player.username} has no level" }
        BotTurn(
            gameId = id,
            kind = record.kind,
            level = level,
            bot = bots.principal(player),
            moves = game.history,
            opponentIsBot = bots.isBot(record.seat(color.opposite)),
        )
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
                cancel(record, entry)
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
                val changes = games.finish(updated, ratingUpdate(updated, color.opposite, now))
                entry.game = null
                val dto = toDto(updated, games.moves(id), changes)
                events.publish(id, ServerEvent.GameUpdated(dto))
                dto
            }
        }
    }

    suspend fun draw(user: UserPrincipal, id: String, action: DrawAction): GameDto = cache.locked(id) { entry ->
        val record = games.findById(id) ?: throw ApiException.notFound("Game")
        requireActive(record)
        val color = record.colorOf(user.id) ?: throw notAPlayer()
        if (bots.isBot(record.whiteId) || bots.isBot(record.blackId)) {
            throw ApiException(
                HttpStatusCode.Conflict,
                "draw_not_available",
                "Draw offers are not available in games against a bot",
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
        val changes = if (updated.phase == GamePhase.FINISHED) {
            entry.game = null
            games.finish(updated, ratingUpdate(updated, winner = null, now))
        } else {
            games.update(updated)
            emptyList()
        }
        val dto = toDto(updated, games.moves(id), changes)
        events.publish(id, ServerEvent.GameUpdated(dto))
        dto
    }

    /**
     * Ends a game by an administrator's decision (docs/ARCHITECTURE.md section 2.11): an ACTIVE
     * or WAITING game is finished by abandonment, without a winner and without touching the
     * ratings, and its subscribers see it end like any other game. A FINISHED game is 409
     * `game_finished`.
     */
    suspend fun abort(id: String): GameDto = cache.locked(id) { entry ->
        val record = games.findById(id) ?: throw ApiException.notFound("Game")
        if (record.phase == GamePhase.FINISHED) throw gameFinished()
        val now = clock.instant()
        val aborted = record.copy(
            phase = GamePhase.FINISHED,
            result = GameResult(null, EndReason.ABANDONMENT),
            drawOfferedBy = null,
            updatedAt = now,
            finishedAt = now,
        )
        games.finish(aborted, rating = null)
        entry.game = null
        val dto = toDto(aborted, games.moves(id), emptyList())
        events.publish(id, ServerEvent.GameUpdated(dto))
        dto
    }

    /**
     * Deletes a game of any phase with its moves and rating changes, for an administrator
     * (section 2.11); the ratings the game led to stay. Nothing is published: the game is
     * simply not found from here on.
     */
    suspend fun delete(id: String) {
        cache.locked(id) { entry ->
            games.findById(id) ?: throw ApiException.notFound("Game")
            games.delete(id)
            entry.game = null
        }
    }

    /**
     * Deletes a WAITING game and reports it as abandoned ([declined] when the invitee turned
     * the invitation down); call under the game's lock.
     */
    private suspend fun cancel(record: GameRecord, entry: GameCache.Entry, declined: Boolean = false): GameDto {
        games.delete(record.id)
        entry.game = null
        val now = clock.instant()
        val cancelled = record.copy(
            phase = GamePhase.FINISHED,
            result = GameResult(null, EndReason.ABANDONMENT),
            updatedAt = now,
            finishedAt = now,
        )
        val dto = toDto(cancelled, emptyList()).copy(declined = declined)
        events.publish(record.id, ServerEvent.GameUpdated(dto))
        return dto
    }

    /**
     * The rating update of a game that just finished, or null when the result does not count:
     * the game is unrated, or it ended before both sides had moved.
     */
    private fun ratingUpdate(record: GameRecord, winner: Color?, now: Instant): RatingUpdate? {
        if (!record.rated || record.moveCount < MIN_RATED_PLIES) return null
        return { before -> ratingService.update(before, winner, now) }
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

    /**
     * The full DTO. [changes] are the rating changes just recorded; without them, a finished
     * rated game's changes are read from the repository.
     */
    private suspend fun toDto(record: GameRecord, moves: List<GameMove>, changes: List<RatingChange>? = null): GameDto {
        val recorded = changes
            ?: if (record.phase == GamePhase.FINISHED && record.rated) ratings.changesFor(record.id) else emptyList()
        val white = recorded.firstOrNull { it.userId == record.whiteId }
        val black = recorded.firstOrNull { it.userId == record.blackId }
        val ratingChanges = if (white != null && black != null) {
            GameRatingChanges(RatingDelta(white.before, white.after), RatingDelta(black.before, black.after))
        } else {
            null
        }
        return toSummaries(listOf(record)).single().withMoves(moves, ratingChanges)
    }

    internal suspend fun toSummaries(records: List<GameRecord>): List<GameSummary> {
        val userIds = records.flatMap { listOfNotNull(it.creatorId, it.whiteId, it.blackId, it.inviteeId) }.toSet()
        val usersById = users.findByIds(userIds)
        val rows = ratings.forUsers(usersById.values.filter { !it.isGuest }.map { it.id })
        fun ref(id: Long?, kind: GameKind): UserRef? =
            id?.let { usersById[it] }?.let { UserRef.of(it, currentRating(it, rows, kind)) }
        return records.map { record ->
            GameSummary(
                id = record.id,
                kind = record.kind,
                visibility = record.visibility,
                status = record.phase,
                rated = record.rated,
                white = ref(record.whiteId, record.kind),
                black = ref(record.blackId, record.kind),
                creator = ref(record.creatorId, record.kind)
                    ?: UserRef(record.creatorId, "deleted", name = null, botLevel = null, rating = null),
                invitee = ref(record.inviteeId, record.kind),
                moveCount = record.moveCount,
                result = record.result,
                drawOfferedBy = record.drawOfferedBy,
                createdAt = record.createdAt.toIso(),
                updatedAt = record.updatedAt.toIso(),
            )
        }
    }

    private companion object {
        /** Both sides must have moved for a result to count: one move each. */
        const val MIN_RATED_PLIES = 2
    }
}
