package ru.werelaxe.chess.server.service

import kotlinx.coroutines.CancellationException
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
import ru.werelaxe.chess.server.model.GameRecord
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark

/**
 * What the computer needs to choose a move: who it is, how strong it plays and the position as
 * the snapshot of the move list ([moves]) taken under the game's lock.
 */
class BotTurn(
    val gameId: String,
    val kind: GameKind,
    val level: EngineLevel,
    val computer: UserPrincipal,
    val moves: List<GameMove>,
)

/**
 * The computer player. Whenever it is the computer's turn in a game against it, asks [moves]
 * for a move and submits it through the normal move pipeline, so that the clients receive the
 * usual `move` events. The position is snapshotted under the game's lock and the move chosen
 * outside it; the provider decides where and how many searches run at a time.
 *
 * A game is never searched twice at the same time: a wake-up that arrives while its game is
 * being played is honoured once the current move is done instead of being lost.
 */
class BotPlayer(
    private val games: GameService,
    private val moves: MoveProvider,
    private val minPause: Duration = DEFAULT_PAUSE,
) {
    private val log = LoggerFactory.getLogger(BotPlayer::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("bot"))

    /** A game in the map is being played; [pending] is a wake-up received meanwhile. Guarded by the map's `compute`. */
    private class Slot {
        var pending = false
    }

    private val inFlight = ConcurrentHashMap<String, Slot>()

    /**
     * Makes the computer move in [record]'s game if it is a game against the computer and the
     * computer is on turn; a no-op otherwise. [movedAt] is when the human's move was applied:
     * the reply then comes at least [minPause] after it. Without it the reply is immediate.
     */
    fun wake(record: GameRecord, movedAt: TimeMark? = null) {
        if (!record.isBotsTurn) return
        var launch = false
        inFlight.compute(record.id) { _, slot ->
            if (slot == null) {
                launch = true
                Slot()
            } else {
                slot.also { it.pending = true }
            }
        }
        if (launch) scope.launch { run(record.id, movedAt) }
    }

    /** Stops the player; searches still running finish but their moves are not submitted. */
    fun close() = scope.cancel()

    private suspend fun run(id: String, movedAt: TimeMark?) {
        var pacing = movedAt
        try {
            do {
                try {
                    play(id, pacing)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Nothing to retry here (the database is unreachable, no engine could
                    // search, or the like); the next look at the game wakes the player again.
                    log.error("Computer move failed in game {}", id, e)
                }
                pacing = null
            } while (keepForReplay(id))
        } catch (e: CancellationException) {
            inFlight.remove(id)
            throw e
        }
    }

    /**
     * Releases the game, unless a wake-up arrived while it was being played: then it stays
     * taken and the result says to play it once more.
     */
    private fun keepForReplay(id: String): Boolean =
        inFlight.compute(id) { _, slot -> slot?.takeIf { it.pending }?.also { it.pending = false } } != null

    /** Chooses and submits a move; a rejected move is followed by a fresh look at the game, a few times at most. */
    private suspend fun play(id: String, movedAt: TimeMark?) {
        var pacing = movedAt
        repeat(MAX_ATTEMPTS) {
            val turn = games.botTurn(id) ?: return
            val move = moves.chooseMove(turn.gameId, turn.kind, turn.level, turn.moves) ?: return
            pacing?.let { mark ->
                val remaining = minPause - mark.elapsedNow()
                if (remaining.isPositive()) delay(remaining)
            }
            pacing = null
            try {
                games.move(turn.computer, id, move)
                return
            } catch (e: ApiException) {
                // The game changed under the search (the human resigned) or the engine erred.
                log.warn("Computer move {} rejected in game {}: {} ({})", move, id, e.code, e.message)
            } catch (e: IllegalMoveException) {
                log.warn("Computer move {} rejected in game {}: {}", move, id, e.message)
            }
        }
        log.error("Giving up on game {}: {} computer moves in a row were rejected", id, MAX_ATTEMPTS)
    }

    companion object {
        /** Shortest pause between a human's move and the computer's reply, so that the reply is perceived as one. */
        val DEFAULT_PAUSE: Duration = 700.milliseconds
        private const val MAX_ATTEMPTS = 3
    }
}
