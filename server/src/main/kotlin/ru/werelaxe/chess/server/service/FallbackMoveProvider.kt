package ru.werelaxe.chess.server.service

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.engine.ThinkPriority

/**
 * Asks [primary] (the engine service) and, when that fails, [fallback] (the in-process
 * engine), so that an unavailable service never stalls a game. Every fallback is logged at
 * WARN with the reason. The one failure that does not fall back is a service that is merely
 * busy for a bot-versus-bot game ([EngineBusyException]): that game waits for the service
 * instead of loading this process when it is busiest, so the exception is passed on.
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
            log.warn("Engine service failed for game {} at ply {}; using the in-process engine: {}", gameId, moves.size, e.toString())
        }
        return fallback.chooseMove(gameId, kind, level, moves, priority)
    }

    override fun close() {
        primary.close()
        fallback.close()
    }
}
