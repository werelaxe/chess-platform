package ru.werelaxe.chess.server

import ru.werelaxe.chess.server.config.AppConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}
