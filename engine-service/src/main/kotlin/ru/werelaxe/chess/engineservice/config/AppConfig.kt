package ru.werelaxe.chess.engineservice.config

/** Engine service configuration; see docs/ARCHITECTURE.md section 2.7 for the environment variables. */
data class AppConfig(
    val port: Int = 8081,
    /** Search threads; the CPUs available to the process by default. */
    val parallelism: Int = Runtime.getRuntime().availableProcessors(),
    /** Requests allowed to wait for a search thread; beyond it the service answers 503. */
    val queueLimit: Int = 4 * parallelism,
    /** Threads a search borrows for its root moves or candidates while they are idle; as many as search threads by default. */
    val helperThreads: Int = defaultHelperThreads(parallelism),
    /** The thinking budget never shrinks below this share of the level's budget. */
    val minBudgetRatio: Double = 0.25,
    /** Replayed games kept for the next request of the same game. */
    val cacheSize: Int = 512,
) {
    init {
        require(parallelism >= 1) { "parallelism must be at least 1" }
        require(queueLimit >= 0) { "queueLimit must not be negative" }
        require(helperThreads >= 0) { "helperThreads must not be negative" }
        require(minBudgetRatio in 0.0..1.0) { "minBudgetRatio must be between 0 and 1" }
        require(cacheSize >= 0) { "cacheSize must not be negative" }
    }

    /** The environment is unusable; the service refuses to start rather than guess. */
    class ConfigurationException(message: String) : RuntimeException(message)

    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): AppConfig {
            val defaults = AppConfig()
            val parallelism = env.int("ENGINE_PARALLELISM") ?: defaults.parallelism
            return try {
                AppConfig(
                    port = env.int("PORT") ?: defaults.port,
                    parallelism = parallelism,
                    queueLimit = env.int("ENGINE_QUEUE_LIMIT") ?: 4 * parallelism,
                    helperThreads = env.int("ENGINE_HELPER_THREADS") ?: defaultHelperThreads(parallelism),
                    minBudgetRatio = env.double("ENGINE_MIN_BUDGET_RATIO") ?: defaults.minBudgetRatio,
                )
            } catch (e: IllegalArgumentException) {
                throw ConfigurationException(e.message ?: "Invalid configuration")
            }
        }

        /** An unset or blank variable means the default (compose passes empty strings for unset values). */
        private fun Map<String, String>.value(name: String): String? = this[name]?.trim()?.takeIf { it.isNotEmpty() }

        private fun Map<String, String>.int(name: String): Int? =
            value(name)?.let { it.toIntOrNull() ?: throw ConfigurationException("$name must be an integer, got '$it'") }

        private fun Map<String, String>.double(name: String): Double? =
            value(name)?.let { it.toDoubleOrNull() ?: throw ConfigurationException("$name must be a number, got '$it'") }
    }
}

/**
 * Half of the search threads, at least one: an idle search then uses most of the cores, while a
 * fully loaded service oversubscribes them by at most one half instead of twice.
 */
fun defaultHelperThreads(parallelism: Int): Int = maxOf(1, parallelism / 2)
