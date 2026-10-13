package ru.werelaxe.chess.server.repository.memory

import kotlinx.coroutines.sync.withLock
import ru.werelaxe.chess.server.model.Setting
import ru.werelaxe.chess.server.repository.SettingsRepository
import java.time.Instant

/** Non-persistent repository for tests and local experiments. */
class InMemorySettingsRepository(private val store: InMemoryStore = InMemoryStore()) : SettingsRepository {
    override suspend fun list(): List<Setting> = store.mutex.withLock { store.settings.values.sortedBy { it.key } }

    override suspend fun upsert(key: String, value: String, updatedAt: Instant) {
        store.mutex.withLock { store.settings[key] = Setting(key, value, updatedAt) }
    }

    override suspend fun delete(key: String) {
        store.mutex.withLock { store.settings.remove(key) }
    }
}
