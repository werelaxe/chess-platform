package ru.werelaxe.chess.server

import ru.werelaxe.chess.server.config.AppConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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

    @Test
    fun matchmakingIsOnByDefault() {
        val config = AppConfig.fromEnvironment(mapOf("JWT_SECRET" to "0123456789abcdef"))
        assertTrue(config.botMatchmaking)
        assertEquals(3, config.botTickMinMinutes)
        assertEquals(10, config.botTickMaxMinutes)
        assertEquals(2, config.botOpenGamesTarget)
        assertEquals(8, config.botJoinAfterMinutes)
        assertEquals(60, config.botInvitationTtlMinutes)
        assertEquals(6, config.botMaxActiveGames)
        assertEquals(4, config.botVsBotPauseSeconds)
        assertEquals(24, config.openGameTtlHours)
    }

    @Test
    fun readsTheMatchmakingSettings() {
        val config = AppConfig.fromEnvironment(
            mapOf(
                "JWT_SECRET" to "0123456789abcdef",
                "BOT_MATCHMAKING" to "False",
                "BOT_TICK_MIN_MINUTES" to "5",
                "BOT_TICK_MAX_MINUTES" to "2",
                "BOT_OPEN_GAMES_TARGET" to "0",
                "BOT_JOIN_AFTER_MINUTES" to "1",
                "BOT_INVITATION_TTL_MINUTES" to "15",
                "BOT_MAX_ACTIVE_GAMES" to "3",
                "BOT_VS_BOT_PAUSE_SECONDS" to "2",
                "OPEN_GAME_TTL_HOURS" to "48",
            ),
        )
        assertEquals(48, config.openGameTtlHours)
        assertFalse(config.botMatchmaking)
        assertEquals(5, config.botTickMinMinutes)
        assertEquals(5, config.botTickMaxMinutes, "the upper bound never falls below the lower one")
        assertEquals(0, config.botOpenGamesTarget)
        assertEquals(1, config.botJoinAfterMinutes)
        assertEquals(15, config.botInvitationTtlMinutes)
        assertEquals(3, config.botMaxActiveGames)
        assertEquals(2, config.botVsBotPauseSeconds)
        // Anything but true/false means the default.
        assertTrue(AppConfig.fromEnvironment(mapOf("JWT_SECRET" to "0123456789abcdef", "BOT_MATCHMAKING" to "off")).botMatchmaking)
    }
}
