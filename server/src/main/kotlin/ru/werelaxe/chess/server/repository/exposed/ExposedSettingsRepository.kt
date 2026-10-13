package ru.werelaxe.chess.server.repository.exposed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import ru.werelaxe.chess.server.db.Settings
import ru.werelaxe.chess.server.model.Setting
import ru.werelaxe.chess.server.repository.SettingsRepository
import java.time.Instant
import java.time.ZoneOffset

class ExposedSettingsRepository(private val db: Database) : SettingsRepository {
    override suspend fun list(): List<Setting> = tx {
        Settings.selectAll().orderBy(Settings.key to SortOrder.ASC).map {
            Setting(it[Settings.key], it[Settings.value], it[Settings.updatedAt].toInstant())
        }
    }

    override suspend fun upsert(key: String, value: String, updatedAt: Instant) {
        tx {
            Settings.upsert {
                it[Settings.key] = key
                it[Settings.value] = value
                it[Settings.updatedAt] = updatedAt.atOffset(ZoneOffset.UTC)
            }
        }
    }

    override suspend fun delete(key: String) {
        tx { Settings.deleteWhere { Settings.key eq key } }
    }

    private suspend fun <T> tx(block: JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) {
        transaction(db) { block() }
    }
}
