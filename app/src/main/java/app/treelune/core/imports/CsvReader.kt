package app.treelune.core.imports

/**
 * The common form of an import: lines of text values named by their column (docs/DATA.md,
 * « Import »). Only reading a source is its own: a CSV gives it almost as it is.
 */
data class ImportTable(val columns: List<String>, val rows: List<List<String>>) {
    /** The cells of [column], one per line, in order. */
    fun cells(column: Int): List<String> = rows.map { it.getOrElse(column) { "" } }
}

/**
 * A CSV as the common form: its first line the columns, then one line per row. The separator is
 * the one of `,`, `;` or a tab that splits the header into the most columns; a value may be quoted,
 * a quote inside written twice, a line break inside a quoted value kept.
 */
object CsvReader {

    /**
     * @param words The shared strings, for why a file does not read
     * @throws IllegalArgumentException on a file without a header, or a quote left open
     */
    fun read(text: String, words: (String) -> String): ImportTable {
        val content = text.removePrefix("﻿")
        val firstLine = content.lineSequence().firstOrNull { it.isNotBlank() } ?: throw IllegalArgumentException(words("import_file_empty"))
        val separator = listOf(',', ';', '\t').maxBy { sep -> split(firstLine, sep, words).size }
        val lines = parse(content, separator, words).filter { line -> line.any { it.isNotBlank() } }
        if (lines.isEmpty()) throw IllegalArgumentException(words("import_file_empty"))
        val columns = lines.first().map { it.trim() }
        return ImportTable(columns, lines.drop(1).map { row -> row.map { it.trim() } })
    }

    private fun split(line: String, separator: Char, words: (String) -> String): List<String> = parse(line, separator, words).firstOrNull() ?: emptyList()

    private fun parse(text: String, separator: Char, words: (String) -> String): List<List<String>> {
        val lines = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' && (quoted || cell.isEmpty()) -> quoted = !quoted
                quoted -> cell.append(c)
                c == separator -> { row.add(cell.toString()); cell.clear() }
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row.add(cell.toString()); cell.clear()
                    lines.add(row); row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (quoted) throw IllegalArgumentException(words("import_file_quote_open"))
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); lines.add(row) }
        return lines
    }
}
