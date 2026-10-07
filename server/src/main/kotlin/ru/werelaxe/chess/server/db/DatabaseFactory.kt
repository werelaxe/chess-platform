package ru.werelaxe.chess.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import ru.werelaxe.chess.server.config.AppConfig

object DatabaseFactory {
    /** Creates the connection pool, applies pending Flyway migrations and connects Exposed. */
    fun connect(config: AppConfig): Database {
        val dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.databaseUrl
                username = config.databaseUser
                password = config.databasePassword
                maximumPoolSize = 10
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_READ_COMMITTED"
                validate()
            },
        )
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        return Database.connect(dataSource)
    }
}
