package ru.werelaxe.chess.server.repository

import ru.werelaxe.chess.server.model.Setting
import java.time.Instant

/** The `settings` table: the runtime overrides of the bots' defaults (docs/ARCHITECTURE.md section 2.11). */
interface SettingsRepository {
    /** Every stored setting, by key. */
    suspend fun list(): List<Setting>

    /** Stores or replaces the value of [key]. */
    suspend fun upsert(key: String, value: String, updatedAt: Instant)

    /** Removes the override of [key], if any, so that its default applies again. */
    suspend fun delete(key: String)
}
