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
        assertEquals(4096, config.botMaxUniverses)
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
                "BOT_MAX_UNIVERSES" to "100",
                "BOT_REMOTE_CONCURRENCY" to "16",
                "GAME_IDLE_MINUTES" to "10",
            ),
        )
        assertEquals("http://engine:8081", config.engineUrl)
        assertEquals(5, config.engineRetrySeconds)
        assertEquals(3, config.botParallelism)
        assertEquals(100, config.botMaxUniverses)
        assertEquals(16, config.botRemoteConcurrency)
        assertEquals(10, config.gameIdleMinutes)
        // Zero switches the ceiling off.
        assertEquals(0, AppConfig.fromEnvironment(mapOf("JWT_SECRET" to "0123456789abcdef", "BOT_MAX_UNIVERSES" to "0")).botMaxUniverses)
    }

    @Test
    fun ignoresMalformedOrNonPositiveNumbers() {
        val config = AppConfig.fromEnvironment(
            mapOf(
                "JWT_SECRET" to "0123456789abcdef",
                "ENGINE_RETRY_SECONDS" to "soon",
                "BOT_PARALLELISM" to "0",
                "BOT_MAX_UNIVERSES" to "-1",
                "BOT_REMOTE_CONCURRENCY" to "-1",
                "GAME_IDLE_MINUTES" to "",
            ),
        )
        assertEquals(30, config.engineRetrySeconds)
        assertEquals(2, config.botParallelism)
        assertEquals(4096, config.botMaxUniverses)
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
        assertEquals(2, config.botLiveGamesTarget)
        assertEquals(0, config.botHumanGamesLimit, "the human-load gate is off by default")
        assertEquals(8, config.botJoinAfterMinutes)
        assertEquals(60, config.botInvitationTtlMinutes)
        assertEquals(6, config.botMaxActiveGames)
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
                "BOT_LIVE_GAMES_TARGET" to "4",
                "BOT_HUMAN_GAMES_LIMIT" to "25",
                "BOT_JOIN_AFTER_MINUTES" to "1",
                "BOT_INVITATION_TTL_MINUTES" to "15",
                "BOT_MAX_ACTIVE_GAMES" to "3",
                "OPEN_GAME_TTL_HOURS" to "48",
            ),
        )
        assertEquals(48, config.openGameTtlHours)
        assertFalse(config.botMatchmaking)
        assertEquals(5, config.botTickMinMinutes)
        assertEquals(5, config.botTickMaxMinutes, "the upper bound never falls below the lower one")
        assertEquals(0, config.botOpenGamesTarget)
        assertEquals(4, config.botLiveGamesTarget)
        assertEquals(0, AppConfig.fromEnvironment(mapOf("JWT_SECRET" to "0123456789abcdef", "BOT_LIVE_GAMES_TARGET" to "0")).botLiveGamesTarget, "zero switches the live games off")
        assertEquals(25, config.botHumanGamesLimit)
        assertEquals(0, AppConfig.fromEnvironment(mapOf("JWT_SECRET" to "0123456789abcdef", "BOT_HUMAN_GAMES_LIMIT" to "0")).botHumanGamesLimit, "zero switches the gate off")
        assertEquals(0, AppConfig.fromEnvironment(mapOf("JWT_SECRET" to "0123456789abcdef", "BOT_HUMAN_GAMES_LIMIT" to "-3")).botHumanGamesLimit, "a negative limit means the default")
        assertEquals(1, config.botJoinAfterMinutes)
        assertEquals(15, config.botInvitationTtlMinutes)
        assertEquals(3, config.botMaxActiveGames)
        // Anything but true/false means the default.
        assertTrue(AppConfig.fromEnvironment(mapOf("JWT_SECRET" to "0123456789abcdef", "BOT_MATCHMAKING" to "off")).botMatchmaking)
    }

    @Test
    fun readsTheStaleGameAge() {
        val base = mapOf("JWT_SECRET" to "0123456789abcdef")
        assertEquals(24, AppConfig.fromEnvironment(base).gameStaleHours)
        assertEquals(48, AppConfig.fromEnvironment(base + ("GAME_STALE_HOURS" to "48")).gameStaleHours)
        assertEquals(0, AppConfig.fromEnvironment(base + ("GAME_STALE_HOURS" to "0")).gameStaleHours, "zero switches the sweep off")
        assertEquals(24, AppConfig.fromEnvironment(base + ("GAME_STALE_HOURS" to "-1")).gameStaleHours, "a negative age means the default")
        assertEquals(24, AppConfig.fromEnvironment(base + ("GAME_STALE_HOURS" to "a day")).gameStaleHours)
    }

    @Test
    fun adminApiIsOffUnlessATokenLongEnoughIsSet() {
        val base = mapOf("JWT_SECRET" to "0123456789abcdef")
        assertNull(AppConfig.fromEnvironment(base).adminToken)
        assertNull(AppConfig.fromEnvironment(base + ("ADMIN_TOKEN" to "   ")).adminToken, "blank means unset")
        assertFailsWith<AppConfig.ConfigurationException> { AppConfig.fromEnvironment(base + ("ADMIN_TOKEN" to "short")) }
        assertEquals("admin-0123456789abcdef", AppConfig.fromEnvironment(base + ("ADMIN_TOKEN" to "admin-0123456789abcdef")).adminToken)
    }

    @Test
    fun botPausesHaveFixedDefaults() {
        val config = AppConfig.fromEnvironment(mapOf("JWT_SECRET" to "0123456789abcdef"))
        assertEquals(700, config.botReplyPauseMillis)
        assertEquals(0, config.botVsBotPauseMillis)
    }
}
