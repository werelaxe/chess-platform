package ru.werelaxe.chess.server.config

/** Server configuration; see docs/ARCHITECTURE.md sections 2.1 and 2.10 for the environment variables. */
data class AppConfig(
    val port: Int = 8080,
    val databaseUrl: String = "jdbc:postgresql://localhost:5432/chess",
    val databaseUser: String = "chess",
    val databasePassword: String = "chess",
    /** HS256 key. Deliberately without a default: see [fromEnvironment]. */
    val jwtSecret: String,
    val jwtTtlDays: Long = 30,
    /** Allowed CORS origins; empty disables CORS entirely (production behind nginx). */
    val corsOrigins: List<String> = listOf("http://localhost:5173"),
    val bcryptCost: Int = 12,
    /** Base URL of the engine service (section 2.7); null means the in-process engine only. */
    val engineUrl: String? = null,
    /** How long an overloaded engine service is retried before the in-process engine takes over. */
    val engineRetrySeconds: Long = 30,
    /** Concurrent in-process searches. */
    val botParallelism: Int = 2,
    /** Concurrent requests to the engine service. */
    val botRemoteConcurrency: Int = 64,
    /** Idle time after which a cached game is dropped from memory. */
    val gameIdleMinutes: Long = 30,
    /** Pause between the moves of a game between two bots. */
    val botVsBotPauseSeconds: Long = 4,
    /** Whether the bots keep the lobby alive by themselves (section 2.10). */
    val botMatchmaking: Boolean = true,
    val botTickMinMinutes: Long = 3,
    val botTickMaxMinutes: Long = 10,
    val botOpenGamesTarget: Int = 2,
    val botJoinAfterMinutes: Long = 8,
    val botInvitationTtlMinutes: Long = 60,
    val botMaxActiveGames: Int = 6,
    /** Age after which a public open game nobody joined is deleted by the matchmaking loop (section 2.10). */
    val openGameTtlHours: Long = 24,
) {
    /** The environment is unusable; the server refuses to start rather than run insecurely. */
    class ConfigurationException(message: String) : RuntimeException(message)

    companion object {
        const val MIN_JWT_SECRET_LENGTH = 16

        fun fromEnvironment(env: Map<String, String> = System.getenv()): AppConfig {
            val jwtSecret = env["JWT_SECRET"].orEmpty()
            if (jwtSecret.isBlank() || jwtSecret.length < MIN_JWT_SECRET_LENGTH) {
                throw ConfigurationException(
                    "JWT_SECRET must be set to a random string of at least $MIN_JWT_SECRET_LENGTH characters",
                )
            }
            val defaults = AppConfig(jwtSecret = jwtSecret)
            val tickMin = env.positiveLong("BOT_TICK_MIN_MINUTES") ?: defaults.botTickMinMinutes
            return defaults.copy(
                port = env["PORT"]?.toIntOrNull() ?: defaults.port,
                databaseUrl = env["DATABASE_URL"] ?: defaults.databaseUrl,
                databaseUser = env["DATABASE_USER"] ?: defaults.databaseUser,
                databasePassword = env["DATABASE_PASSWORD"] ?: defaults.databasePassword,
                jwtTtlDays = env["JWT_TTL_DAYS"]?.toLongOrNull() ?: defaults.jwtTtlDays,
                corsOrigins = env["CORS_ORIGINS"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: defaults.corsOrigins,
                engineUrl = env["ENGINE_URL"]?.trim()?.takeIf { it.isNotEmpty() },
                engineRetrySeconds = env.positiveLong("ENGINE_RETRY_SECONDS") ?: defaults.engineRetrySeconds,
                botParallelism = env.positiveInt("BOT_PARALLELISM") ?: defaults.botParallelism,
                botRemoteConcurrency = env.positiveInt("BOT_REMOTE_CONCURRENCY") ?: defaults.botRemoteConcurrency,
                gameIdleMinutes = env.positiveLong("GAME_IDLE_MINUTES") ?: defaults.gameIdleMinutes,
                botVsBotPauseSeconds = env.positiveLong("BOT_VS_BOT_PAUSE_SECONDS") ?: defaults.botVsBotPauseSeconds,
                botMatchmaking = env["BOT_MATCHMAKING"]?.trim()?.lowercase()?.toBooleanStrictOrNull() ?: defaults.botMatchmaking,
                botTickMinMinutes = tickMin,
                // The upper bound never falls below the lower one, so the pause stays well defined.
                botTickMaxMinutes = (env.positiveLong("BOT_TICK_MAX_MINUTES") ?: defaults.botTickMaxMinutes).coerceAtLeast(tickMin),
                botOpenGamesTarget = env.nonNegativeInt("BOT_OPEN_GAMES_TARGET") ?: defaults.botOpenGamesTarget,
                botJoinAfterMinutes = env.positiveLong("BOT_JOIN_AFTER_MINUTES") ?: defaults.botJoinAfterMinutes,
                botInvitationTtlMinutes = env.positiveLong("BOT_INVITATION_TTL_MINUTES") ?: defaults.botInvitationTtlMinutes,
                botMaxActiveGames = env.nonNegativeInt("BOT_MAX_ACTIVE_GAMES") ?: defaults.botMaxActiveGames,
                openGameTtlHours = env.positiveLong("OPEN_GAME_TTL_HOURS") ?: defaults.openGameTtlHours,
            )
        }

        /** A missing, malformed or non-positive value means the default, like the other numeric variables. */
        private fun Map<String, String>.positiveInt(name: String): Int? = this[name]?.toIntOrNull()?.takeIf { it > 0 }

        private fun Map<String, String>.positiveLong(name: String): Long? = this[name]?.toLongOrNull()?.takeIf { it > 0 }

        /** Zero is meaningful for a target or a cap (it switches the behaviour off). */
        private fun Map<String, String>.nonNegativeInt(name: String): Int? = this[name]?.toIntOrNull()?.takeIf { it >= 0 }
    }
}
