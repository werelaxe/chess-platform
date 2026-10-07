package ru.werelaxe.chess.server.db

import org.jetbrains.exposed.v1.core.ColumnType
import org.postgresql.util.PGobject

/** A PostgreSQL JSONB column exposed as its raw JSON text. */
class JsonbColumnType : ColumnType<String>() {
    override fun sqlType(): String = "JSONB"

    override fun valueFromDB(value: Any): String? = when (value) {
        is PGobject -> value.value
        is String -> value
        else -> value.toString()
    }

    override fun notNullValueToDB(value: String): Any = PGobject().apply {
        type = "jsonb"
        this.value = value
    }

    override fun nonNullValueToString(value: String): String = "'${value.replace("'", "''")}'::jsonb"
}
