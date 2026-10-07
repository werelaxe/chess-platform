package ru.werelaxe.chess.server.service

import io.ktor.client.engine.cio.CIO
import org.slf4j.LoggerFactory
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.engine.ChessEngine
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.server.config.AppConfig
import kotlin.time.Duration.Companion.seconds

/**
 * Where the computer's moves come from. The implementations search the position reached by
 * [chooseMove]'s move list either in this process or in the engine service; [FallbackMoveProvider]
 * combines the two. A provider may hold resources (an HTTP client): the application closes it
 * when it stops.
 */
interface MoveProvider : AutoCloseable {
    /**
     * The move the computer plays at [level] in the position reached after [moves] in a game of
     * [kind]; null when the game is already over. An observation comes back without an outcome.
     * [gameId] lets a remote engine cache the replayed position between consecutive requests.
     */
    suspend fun chooseMove(gameId: String, kind: GameKind, level: EngineLevel, moves: List<GameMove>): GameMove?

    override fun close() {}

    companion object {
        private val log = LoggerFactory.getLogger(MoveProvider::class.java)

        /**
         * The provider described by [config]: the engine service at `ENGINE_URL` with the
         * in-process engine as a fallback, or the in-process engine alone when the URL is unset.
         * [engines] builds the in-process engine for a level.
         */
        fun fromConfig(
            config: AppConfig,
            engines: (EngineLevel) -> ChessEngine = { ChessEngine(it) },
        ): MoveProvider {
            val inProcess = InProcessMoveProvider(engines, config.botParallelism)
            val url = config.engineUrl
            if (url == null) {
                log.info("Computer moves are searched in process, {} at a time", config.botParallelism)
                return inProcess
            }
            log.info(
                "Computer moves come from the engine service at {} (at most {} requests in flight, " +
                    "overload retried for {} s); the in-process engine is the fallback",
                url,
                config.botRemoteConcurrency,
                config.engineRetrySeconds,
            )
            val remote = RemoteMoveProvider(
                CIO.create(),
                url,
                retryFor = config.engineRetrySeconds.seconds,
                concurrency = config.botRemoteConcurrency,
            )
            return FallbackMoveProvider(remote, inProcess)
        }
    }
}
