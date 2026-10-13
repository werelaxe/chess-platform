package ru.werelaxe.chess.server.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.IllegalMoveException
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.model.GameRecord
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.TimeMark

/**
 * What a bot needs to choose a move: who it is, how strong it plays and the position as the
 * snapshot of the move list ([moves]) taken under the game's lock. [opponentIsBot] selects
 * which of the two pauses of the [Settings] applies before the move.
 */
class BotTurn(
    val gameId: String,
    val kind: GameKind,
    val level: EngineLevel,
    val bot: UserPrincipal,
    val moves: List<GameMove>,
    val opponentIsBot: Boolean,
)

/**
 * The bot player. Whenever the seat on turn in a game belongs to a bot, asks [moves] for a
 * move and submits it through the normal move pipeline, so that the clients receive the
 * usual `move` events. The position is snapshotted under the game's lock and the move chosen
 * outside it; the provider decides where and how many searches run at a time. The pauses are
 * read from [settings] before every move, so a change applies without a restart.
 *
 * A game is never searched twice at the same time: a wake-up that arrives while its game is
 * being played is honoured once the current move is done instead of being lost.
 */
class BotPlayer(
    private val games: GameService,
    private val bots: Bots,
    private val moves: MoveProvider,
    private val settings: Settings,
) {
    private val log = LoggerFactory.getLogger(BotPlayer::class.java)

    /** An [Error] out of a search (a stack overflow in the engine, say) ends that game's run; it is logged here. */
    private val crashes = CoroutineExceptionHandler { _, e -> log.error("Bot player crashed", e) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("bot") + crashes)

    /**
     * A game in the map is being played; [pending] is a wake-up received meanwhile and
     * [movedAt] its pacing mark. Guarded by the map's `compute`.
     */
    private class Slot {
        var pending = false
        var movedAt: TimeMark? = null
    }

    /** A wake-up to honour once the current move is done. */
    private class Replay(val movedAt: TimeMark?)

    private val inFlight = ConcurrentHashMap<String, Slot>()

    /** Whether a bot is on turn in [record]'s game, judged from the move count alone. */
    fun isBotsTurn(record: GameRecord): Boolean =
        record.phase == GamePhase.ACTIVE && bots.isBot(record.seat(record.sideToMove))

    /**
     * Makes the bot move in [record]'s game if a bot is on turn; a no-op otherwise. [movedAt]
     * is when the previous move was applied: the reply then comes at least the pause after it.
     * Without it the reply is immediate.
     */
    fun wake(record: GameRecord, movedAt: TimeMark? = null) {
        if (!isBotsTurn(record)) return
        var launched: Slot? = null
        inFlight.compute(record.id) { _, slot ->
            if (slot == null) {
                Slot().also { launched = it }
            } else {
                slot.also {
                    it.pending = true
                    it.movedAt = movedAt ?: it.movedAt
                }
            }
        }
        launched?.let { slot -> scope.launch { run(record.id, slot, movedAt) } }
    }

    /** Stops the player; searches still running finish but their moves are not submitted. */
    fun close() = scope.cancel()

    /**
     * Plays until no wake-up is pending. Whatever ends the loop, [slot] is released on the
     * way out (and only that slot: a later wake-up may already have taken the game again), so
     * that nothing can leave a game muted until the next restart.
     */
    private suspend fun run(id: String, slot: Slot, movedAt: TimeMark?) {
        var pacing = movedAt
        try {
            while (true) {
                try {
                    play(id, pacing)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Nothing to retry here (the database is unreachable, no engine could
                    // search, or the like); the next look at the game wakes the player again.
                    log.error("Bot move failed in game {}", id, e)
                }
                pacing = (keepForReplay(id) ?: break).movedAt
            }
        } finally {
            inFlight.remove(id, slot)
        }
    }

    /**
     * Releases the game, unless a wake-up arrived while it was being played: then it stays
     * taken and the wake-up is returned to be played once more.
     */
    private fun keepForReplay(id: String): Replay? {
        var replay: Replay? = null
        inFlight.compute(id) { _, slot ->
            if (slot == null || !slot.pending) return@compute null
            replay = Replay(slot.movedAt)
            slot.pending = false
            slot.movedAt = null
            slot
        }
        return replay
    }

    /** Chooses and submits a move; a rejected move is followed by a fresh look at the game, a few times at most. */
    private suspend fun play(id: String, movedAt: TimeMark?) {
        var pacing = movedAt
        repeat(MAX_ATTEMPTS) {
            val turn = games.botTurn(id) ?: return
            val move = moves.chooseMove(turn.gameId, turn.kind, turn.level, turn.moves) ?: return
            // The pause makes a reply to a human feel like one; two bots have their own, nil by default.
            val pause = if (turn.opponentIsBot) settings.vsBotPause else settings.replyPause
            pacing?.let { mark ->
                val remaining = pause - mark.elapsedNow()
                if (remaining.isPositive()) delay(remaining)
            }
            pacing = null
            try {
                games.move(turn.bot, id, move)
                return
            } catch (e: ApiException) {
                // The game changed under the search (the opponent resigned) or the engine erred.
                log.warn("Bot move {} rejected in game {}: {} ({})", move, id, e.code, e.message)
            } catch (e: IllegalMoveException) {
                log.warn("Bot move {} rejected in game {}: {}", move, id, e.message)
            }
        }
        log.error("Giving up on game {}: {} bot moves in a row were rejected", id, MAX_ATTEMPTS)
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
