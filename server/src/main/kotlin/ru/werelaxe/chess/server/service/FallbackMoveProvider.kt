package ru.werelaxe.chess.server.service

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.engine.EngineLevel

/**
 * Asks [primary] (the engine service) and, when that fails for any reason, [fallback] (the
 * in-process engine), so that an unavailable service never stalls a game. Every fallback is
 * logged at WARN with the reason.
 */
class FallbackMoveProvider(
    private val primary: MoveProvider,
    private val fallback: MoveProvider,
) : MoveProvider {
    private val log = LoggerFactory.getLogger(FallbackMoveProvider::class.java)

    override suspend fun chooseMove(gameId: String, kind: GameKind, level: EngineLevel, moves: List<GameMove>): GameMove? {
        try {
            return primary.chooseMove(gameId, kind, level, moves)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Engine service failed for game {} at ply {}; using the in-process engine: {}", gameId, moves.size, e.toString())
        }
        return fallback.chooseMove(gameId, kind, level, moves)
    }

    override fun close() {
        primary.close()
        fallback.close()
    }
}
