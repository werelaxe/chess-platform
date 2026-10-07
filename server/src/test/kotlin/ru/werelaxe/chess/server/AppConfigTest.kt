package ru.werelaxe.chess.server

import ru.werelaxe.chess.server.config.AppConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AppConfigTest {
    @Test
    fun refusesAMissingBlankOrShortJwtSecret() {
        assertFailsWith<AppConfig.ConfigurationException> { AppConfig.fromEnvironment(emptyMap()) }
        assertFailsWith<AppConfig.ConfigurationException> { AppConfig.fromEnvironment(mapOf("JWT_SECRET" to "")) }
        assertFailsWith<AppConfig.ConfigurationException> { AppConfig.fromEnvironment(mapOf("JWT_SECRET" to " ".repeat(20))) }
        assertFailsWith<AppConfig.ConfigurationException> { AppConfig.fromEnvironment(mapOf("JWT_SECRET" to "short")) }
    }

    @Test
    fun readsTheEnvironment() {
        val config = AppConfig.fromEnvironment(
            mapOf("JWT_SECRET" to "0123456789abcdef", "PORT" to "9090", "CORS_ORIGINS" to ""),
        )
        assertEquals("0123456789abcdef", config.jwtSecret)
        assertEquals(9090, config.port)
        assertEquals(emptyList(), config.corsOrigins)
        assertEquals(30, config.jwtTtlDays)
        assertEquals(12, config.bcryptCost)
    }

    @Test
    fun usesTheInProcessEngineByDefault() {
        val config = AppConfig.fromEnvironment(mapOf("JWT_SECRET" to "0123456789abcdef"))
        assertNull(config.engineUrl)
        assertEquals(30, config.engineRetrySeconds)
        assertEquals(2, config.botParallelism)
        assertEquals(64, config.botRemoteConcurrency)
        assertEquals(30, config.gameIdleMinutes)
        // A blank URL is as good as none.
        assertNull(AppConfig.fromEnvironment(mapOf("JWT_SECRET" to "0123456789abcdef", "ENGINE_URL" to " ")).engineUrl)
    }

    @Test
    fun readsTheEngineServiceSettings() {
        val config = AppConfig.fromEnvironment(
            mapOf(
                "JWT_SECRET" to "0123456789abcdef",
                "ENGINE_URL" to "http://engine:8081",
                "ENGINE_RETRY_SECONDS" to "5",
                "BOT_PARALLELISM" to "3",
                "BOT_REMOTE_CONCURRENCY" to "16",
                "GAME_IDLE_MINUTES" to "10",
            ),
        )
        assertEquals("http://engine:8081", config.engineUrl)
        assertEquals(5, config.engineRetrySeconds)
        assertEquals(3, config.botParallelism)
        assertEquals(16, config.botRemoteConcurrency)
        assertEquals(10, config.gameIdleMinutes)
    }

    @Test
    fun ignoresMalformedOrNonPositiveNumbers() {
        val config = AppConfig.fromEnvironment(
            mapOf(
                "JWT_SECRET" to "0123456789abcdef",
                "ENGINE_RETRY_SECONDS" to "soon",
                "BOT_PARALLELISM" to "0",
                "BOT_REMOTE_CONCURRENCY" to "-1",
                "GAME_IDLE_MINUTES" to "",
            ),
        )
        assertEquals(30, config.engineRetrySeconds)
        assertEquals(2, config.botParallelism)
        assertEquals(64, config.botRemoteConcurrency)
        assertEquals(30, config.gameIdleMinutes)
    }
}
