package ru.werelaxe.chess.server.config

/** Server configuration; see docs/ARCHITECTURE.md section 2.1 for the environment variables. */
data class AppConfig(
    val port: Int = 8080,
    val databaseUrl: String = "jdbc:postgresql://localhost:5432/chess",
    val databaseUser: String = "chess",
    val databasePassword: String = "chess",
    val jwtSecret: String = DEV_JWT_SECRET,
    val jwtTtlDays: Long = 30,
    /** Allowed CORS origins; empty disables CORS entirely (production behind nginx). */
    val corsOrigins: List<String> = listOf("http://localhost:5173"),
    val bcryptCost: Int = 12,
) {
    companion object {
        const val DEV_JWT_SECRET = "dev-only-insecure-jwt-secret"

        fun fromEnvironment(env: Map<String, String> = System.getenv()): AppConfig {
            val defaults = AppConfig()
            return AppConfig(
                port = env["PORT"]?.toIntOrNull() ?: defaults.port,
                databaseUrl = env["DATABASE_URL"] ?: defaults.databaseUrl,
                databaseUser = env["DATABASE_USER"] ?: defaults.databaseUser,
                databasePassword = env["DATABASE_PASSWORD"] ?: defaults.databasePassword,
                jwtSecret = env["JWT_SECRET"]?.takeIf { it.isNotBlank() } ?: defaults.jwtSecret,
                jwtTtlDays = env["JWT_TTL_DAYS"]?.toLongOrNull() ?: defaults.jwtTtlDays,
                corsOrigins = env["CORS_ORIGINS"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: defaults.corsOrigins,
            )
        }
    }
}
