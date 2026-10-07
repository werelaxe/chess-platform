package ru.werelaxe.chess.engineservice

import ru.werelaxe.chess.engineservice.config.AppConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppConfigTest {
    @Test
    fun defaultsFollowTheAvailableProcessors() {
        val config = AppConfig.fromEnvironment(emptyMap())
        assertEquals(8081, config.port)
        assertEquals(Runtime.getRuntime().availableProcessors(), config.parallelism)
        assertEquals(4 * config.parallelism, config.queueLimit)
        assertEquals(0.25, config.minBudgetRatio)
    }

    @Test
    fun readsTheEnvironment() {
        val config = AppConfig.fromEnvironment(
            mapOf("PORT" to "9000", "ENGINE_PARALLELISM" to "3", "ENGINE_QUEUE_LIMIT" to "5", "ENGINE_MIN_BUDGET_RATIO" to "0.5"),
        )
        assertEquals(AppConfig(port = 9000, parallelism = 3, queueLimit = 5, minBudgetRatio = 0.5), config)
    }

    @Test
    fun blankValuesMeanTheDefaults() {
        val config = AppConfig.fromEnvironment(mapOf("ENGINE_PARALLELISM" to "2", "ENGINE_QUEUE_LIMIT" to "", "PORT" to " "))
        assertEquals(2, config.parallelism)
        assertEquals(8, config.queueLimit)
        assertEquals(8081, config.port)
    }

    @Test
    fun refusesInvalidValues() {
        assertFailsWith<AppConfig.ConfigurationException> { AppConfig.fromEnvironment(mapOf("ENGINE_PARALLELISM" to "many")) }
        assertFailsWith<AppConfig.ConfigurationException> { AppConfig.fromEnvironment(mapOf("ENGINE_PARALLELISM" to "0")) }
        assertFailsWith<AppConfig.ConfigurationException> { AppConfig.fromEnvironment(mapOf("ENGINE_QUEUE_LIMIT" to "-1")) }
        assertFailsWith<AppConfig.ConfigurationException> { AppConfig.fromEnvironment(mapOf("ENGINE_MIN_BUDGET_RATIO" to "1.5")) }
    }
}
