package ru.werelaxe.chess.server.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.server.repository.GameRepository
import java.time.Clock
import java.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Finishes the games their players walked away from (docs/ARCHITECTURE.md section 2.3): an
 * ACTIVE game in which nobody has moved for longer than `game.stale_hours` (section 2.11;
 * [Settings.staleGameAge], read before every sweep, zero switches the sweep off), judged by
 * the record's `last_move_at` (the latest move, or the start of the game; a draw offer does
 * not count), is forfeited by the player on turn ([GameService.forfeitStale]): the other side
 * wins by abandonment, with the rating update of a resignation when the game is rated, and
 * the subscribers see it end. A game with a bot on turn is the exception: nobody walked away
 * from it, so it ends with no winner and no rating change. [loop] sweeps every
 * [SWEEP_INTERVAL]; a sweep finishes at most [BATCH] games, the longest idle first, and
 * leaves the rest to the next one. Every candidate is checked again under its lock, so a game
 * that got a move between the listing and the forfeit stays alive. Tests drive [sweep]
 * directly with a [clock] they move by hand.
 */
class StaleGames(
    private val games: GameService,
    private val repository: GameRepository,
    private val settings: Settings,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(StaleGames::class.java)

    /** Sweeps every [SWEEP_INTERVAL] forever; cancelled with the application. */
    suspend fun loop() {
        while (true) {
            delay(SWEEP_INTERVAL)
            try {
                sweep()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Stale game sweep failed", e)
            }
        }
    }

    /** Finishes the ACTIVE games idle for longer than [Settings.staleGameAge], at most [BATCH]; returns how many. */
    suspend fun sweep(): Int {
        val age = settings.staleGameAge
        if (age.isZero) return 0
        val now = clock.instant()
        val cutoff = now - age
        var finished = 0
        for (game in repository.listActiveGamesLastMovedBefore(cutoff, BATCH)) {
            // Checked again under the game's lock: a move since the listing keeps the game alive.
            val dto = games.forfeitStale(game.id, cutoff) ?: continue
            val since = game.lastMoveAt ?: game.updatedAt
            val idle = Duration.between(since, now)
            val winner = dto.result?.winner
            val onTurn = if (game.sideToMove == Color.WHITE) dto.white else dto.black
            if (winner == null) {
                log.info(
                    "Finished the stale game {} without a winner: the bot {} ({}) was on turn; idle for {} since {}, {} moves",
                    game.id,
                    onTurn?.username,
                    game.sideToMove,
                    idle,
                    since,
                    game.moveCount,
                )
            } else {
                log.info(
                    "Forfeited the stale game {} to {} by abandonment: {} ({}) was on turn, {} game{}; idle for {} since {}, {} moves",
                    game.id,
                    winner,
                    onTurn?.username,
                    game.sideToMove,
                    if (dto.rated) "rated" else "unrated",
                    if (dto.ratingChanges != null) ", ratings updated" else "",
                    idle,
                    since,
                    game.moveCount,
                )
            }
            finished++
        }
        return finished
    }

    companion object {
        /** How often the application sweeps; a game is finished within this much of going stale. */
        val SWEEP_INTERVAL = 10.minutes

        /** Games finished per sweep at most; the rest wait for the next one. */
        const val BATCH = 100
    }
}
