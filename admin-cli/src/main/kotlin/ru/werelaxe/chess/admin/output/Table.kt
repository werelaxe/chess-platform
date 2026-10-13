package ru.werelaxe.chess.admin.output

/** Plain-text layouts: aligned columns separated by two spaces, no trailing blanks, no separators. */
object Table {
    /** [headers] and every row must have the same number of cells; an empty [rows] renders the headers alone. */
    fun render(headers: List<String>, rows: List<List<String>>): String {
        require(rows.all { it.size == headers.size }) { "every row needs ${headers.size} cells" }
        val widths = headers.indices.map { column ->
            maxOf(headers[column].length, rows.maxOfOrNull { it[column].length } ?: 0)
        }
        fun line(cells: List<String>) =
            cells.mapIndexed { column, cell -> cell.padEnd(widths[column]) }.joinToString("  ").trimEnd()
        return (listOf(line(headers)) + rows.map(::line)).joinToString("\n")
    }

    /** `Label:  value` lines with the values aligned. */
    fun details(pairs: List<Pair<String, String>>): String {
        val width = pairs.maxOf { it.first.length } + 1
        return pairs.joinToString("\n") { (label, value) -> "$label:".padEnd(width) + "  " + value }
    }
}
