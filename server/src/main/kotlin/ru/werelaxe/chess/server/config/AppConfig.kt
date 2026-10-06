package ru.werelaxe.chess.server.config

/** Server configuration; see docs/ARCHITECTURE.md section 2.1 for the environment variables. */
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
            return defaults.copy(
                port = env["PORT"]?.toIntOrNull() ?: defaults.port,
                databaseUrl = env["DATABASE_URL"] ?: defaults.databaseUrl,
                databaseUser = env["DATABASE_USER"] ?: defaults.databaseUser,
                databasePassword = env["DATABASE_PASSWORD"] ?: defaults.databasePassword,
                jwtTtlDays = env["JWT_TTL_DAYS"]?.toLongOrNull() ?: defaults.jwtTtlDays,
                corsOrigins = env["CORS_ORIGINS"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: defaults.corsOrigins,
            )
        }
    }
}
