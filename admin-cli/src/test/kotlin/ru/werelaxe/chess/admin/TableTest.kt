package ru.werelaxe.chess.admin

import ru.werelaxe.chess.admin.output.Table
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TableTest {
    @Test
    fun columnsAreAlignedWithoutTrailingBlanks() {
        val text = Table.render(
            listOf("KEY", "VALUE", "NOTE"),
            listOf(listOf("a", "1", ""), listOf("longer-key", "22", "x")),
        )
        assertEquals(
            """
            KEY         VALUE  NOTE
            a           1
            longer-key  22     x
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun anEmptyTableIsItsHeader() {
        assertEquals("ID  KIND", Table.render(listOf("ID", "KIND"), emptyList()))
    }

    @Test
    fun aRowWithTheWrongWidthIsRejected() {
        assertFailsWith<IllegalArgumentException> { Table.render(listOf("A", "B"), listOf(listOf("1"))) }
    }

    @Test
    fun detailsAlignTheValues() {
        assertEquals(
            """
            Username:  nick
            Level:     NOVICE
            Legacy:    no
            """.trimIndent(),
            Table.details(listOf("Username" to "nick", "Level" to "NOVICE", "Legacy" to "no")),
        )
    }
}
