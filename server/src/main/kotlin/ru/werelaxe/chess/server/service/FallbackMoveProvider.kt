package ru.werelaxe.chess.server.service

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.engine.ThinkPriority

/**
 * Asks [primary] (the engine service) and, when that fails for a human's move
 * ([ThinkPriority.HUMAN]), [fallback] (the in-process engine), so that an unavailable service
 * never stalls a game somebody is waiting at. Every fallback is logged at WARN with the reason.
 *
 * A bot-versus-bot move ([ThinkPriority.BOT]) never falls back: nobody waits at the board, and
 * searching in this process exactly when the service is overloaded or down would load the API
 * with the very searches that overloaded or crashed the service (a fog position of thousands
 * of universes exhausts a heap). A busy service arrives as an [EngineBusyException] already
 * ([RemoteMoveProvider] raises it once its retries are spent) and is rethrown as it is; any
 * other failure (a connection error, a timeout, an error answer: [EngineServiceException] and
 * the like) is logged at WARN without a stack trace and turned into an [EngineBusyException].
 * Either way [BotPlayer] treats it as "wait for the next matchmaking tick" and logs the wait
 * at INFO.
 */
class FallbackMoveProvider(
    private val primary: MoveProvider,
    private val fallback: MoveProvider,
) : MoveProvider {
    private val log = LoggerFactory.getLogger(FallbackMoveProvider::class.java)

    override suspend fun chooseMove(
        gameId: String,
        kind: GameKind,
        level: EngineLevel,
        moves: List<GameMove>,
        priority: ThinkPriority,
    ): GameMove? {
        try {
            return primary.chooseMove(gameId, kind, level, moves, priority)
        } catch (e: CancellationException) {
            throw e
        } catch (e: EngineBusyException) {
            throw e
        } catch (e: Exception) {
            if (priority == ThinkPriority.BOT) {
                val reason = "Engine service unavailable for bot-versus-bot game $gameId at ply ${moves.size}: $e"
                log.warn("{}; the game waits", reason)
                throw EngineBusyException(reason)
            }
            log.warn("Engine service failed for game {} at ply {}; using the in-process engine: {}", gameId, moves.size, e.toString())
        }
        return fallback.chooseMove(gameId, kind, level, moves, priority)
    }

    override fun close() {
        primary.close()
        fallback.close()
    }
}
