package app.treelune.core.imports

import app.treelune.core.fields.CoreFields
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.toFieldDefinition
import app.treelune.core.fields.toJson
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

/** Where a column goes. */
enum class ColumnTarget {
    /** The name, which finds an entry already there (structured data): it is updated, not duplicated */
    KEY,
    /** A field the tool has: its path ("extra.kcal", "timestamp") */
    FIELD,
    /** A new field of the user, created with the import */
    NEW,
    /** Left out */
    IGNORE
}

/** What is done with one column: its target and the writing its cells are read in. */
data class ColumnDeclaration(
    val column: String,
    val target: ColumnTarget,
    val field: String? = null,
    val newField: FieldDefinition? = null,
    val writing: Writing? = null
) {
    fun toJson(): JSONObject = JSONObject().put("column", column).put("target", target.name).apply {
        field?.let { put("field", it) }
        newField?.let { put("new_field", it.toJson()) }
        writing?.let { put("writing", it.name) }
    }

    companion object {
        fun fromJson(json: JSONObject) = ColumnDeclaration(
            column = json.getString("column"),
            target = ColumnTarget.valueOf(json.getString("target")),
            field = json.optString("field").takeIf { it.isNotEmpty() },
            newField = json.optJSONObject("new_field")?.let { it.put("name", it.optString("name")).toFieldDefinition() },
            writing = json.optString("writing").takeIf { it.isNotEmpty() }?.let { Writing.valueOf(it) }
        )
    }
}

/** What the detection proposes for a column, to confirm or correct before importing. */
data class ColumnProposal(
    val declaration: ColumnDeclaration,
    /** The writings that read every cell with different results: left for the user to choose */
    val ambiguous: List<Writing> = emptyList(),
    /** A cell and what the writing reads in it, chosen to show the reading */
    val example: Pair<String, Any?>? = null,
    /** The lines (from 1, after the header) whose cell the writing does not read */
    val unreadable: List<Int> = emptyList()
)

/** One line to write: its name when it has one, and its values by path, a new field's by its column. */
data class PlannedLine(val line: Int, val name: String?, val values: Map<String, Any>, val newValues: Map<String, Any>)

/** A line refused, with the column and why. */
data class LineRefusal(val line: Int, val column: String, val cell: String, val reason: String)

/** The lines an import writes and those it refuses. */
data class Plan(val lines: List<PlannedLine>, val refused: List<LineRefusal>)

/**
 * The detection and the plan of an import (docs/DATA.md, « Import »): pure, the file read into its
 * common form and the tool's fields given.
 */
object ImportPlanner {

    /** A header fixing its type, "kcal [NUMERIC]": the name, then the type. */
    private val TYPED_HEADER = Regex("^(.*?)\\s*\\[([A-Z]+)]$")

    /** The types a new column is tried as, from the most demanding to TEXT, which reads everything. */
    private val DETECTION_ORDER = listOf(
        FieldType.DATETIME, FieldType.DATE, FieldType.TIME, FieldType.DURATION, FieldType.BOOLEAN, FieldType.NUMERIC, FieldType.CHOICE, FieldType.TEXT
    )

    /**
     * What each column should be, read in the whole file (a single "28/09" settles day and month):
     * the name as the key when the tool finds its entries by it, a column naming a field of the
     * tool that field, any other a new field of the type and writing that read all its cells.
     *
     * @param fields The tool's fields a column may fill, by path
     * @param uniqueName Whether the tool finds an entry by its name (the key)
     */
    fun detect(table: ImportTable, fields: Map<String, FieldDefinition>, uniqueName: Boolean, zone: ZoneId): List<ColumnProposal> =
        table.columns.mapIndexed { index, header ->
            val cells = table.cells(index)
            val typed = TYPED_HEADER.matchEntire(header)
            val label = typed?.groupValues?.get(1)?.trim() ?: header
            val fixedType = typed?.groupValues?.get(2)?.let { t -> FieldType.entries.firstOrNull { it.name == t } }
            val existing = fields.entries.firstOrNull { (path, field) ->
                path.substringAfter('.').equals(label, ignoreCase = true) || field.displayName.equals(label, ignoreCase = true)
            }
            when {
                existing?.key == "name" && uniqueName -> ColumnProposal(ColumnDeclaration(header, ColumnTarget.KEY), example = cells.firstOrNull { it.isNotBlank() }?.let { it to it })
                existing != null -> propose(header, cells, listOf(existing.value.type), zone) { writing ->
                    ColumnDeclaration(header, ColumnTarget.FIELD, field = existing.key, writing = writing)
                }
                else -> propose(header, cells, fixedType?.let { listOf(it) } ?: DETECTION_ORDER, zone) { writing ->
                    val type = writing?.type ?: fixedType ?: FieldType.TEXT
                    ColumnDeclaration(header, ColumnTarget.NEW, newField = FieldDefinition(
                        name = "", displayName = label, description = null, type = type, alwaysVisible = false,
                        config = defaultConfig(type, cells, writing)
                    ), writing = writing)
                }
            }
        }

    /** The first type of [types] some writing reads every cell of; the writing, or none when several disagree. */
    private fun propose(header: String, cells: List<String>, types: List<FieldType>, zone: ZoneId, declare: (Writing?) -> ColumnDeclaration): ColumnProposal {
        val filled = cells.withIndex().filter { it.value.isNotBlank() }
        for (type in types) {
            if (type == FieldType.CHOICE && !fewValues(filled.map { it.value })) continue
            val reading = Writing.of(type).filter { writing -> filled.all { writing.read(it.value, zone) is CellRead.Value } }
            if (reading.isEmpty()) continue
            val distinct = reading.distinctBy { writing -> filled.map { (writing.read(it.value, zone) as CellRead.Value).value } }
            val chosen = distinct.singleOrNull() ?: reading.takeIf { it.size == 1 }?.single()
            val example = pickExample(filled.map { it.value })?.let { cell -> cell to chosen?.let { (it.read(cell, zone) as? CellRead.Value)?.value } }
            return ColumnProposal(declare(chosen), ambiguous = if (chosen == null) reading else emptyList(), example = example)
        }
        // No writing of its type reads every cell: the lines it does not read are named
        val type = types.first()
        val writing = Writing.of(type).firstOrNull()
        return ColumnProposal(declare(writing),
            unreadable = filled.filter { writing == null || writing.read(it.value, zone) !is CellRead.Value }.map { it.index + 1 },
            example = pickExample(filled.map { it.value })?.let { it to null })
    }

    /** A choice is proposed when the values are few, each repeated. */
    private fun fewValues(values: List<String>): Boolean {
        val distinct = values.flatMap { it.split(';') }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().size
        return distinct in 2..12 && values.size >= distinct * 2
    }

    /** A cell that shows the reading: one where day and month cannot be swapped, else the first. */
    private fun pickExample(cells: List<String>): String? =
        cells.firstOrNull { Regex("^(1[3-9]|2\\d|3[01])[/.\\-]").containsMatchIn(it) } ?: cells.firstOrNull()

    /** What a new field of [type] needs: a number its decimals, a choice its options from the file. */
    private fun defaultConfig(type: FieldType, cells: List<String>, writing: Writing?): Map<String, Any>? = when (type) {
        FieldType.NUMERIC -> mapOf("decimals" to cells.mapNotNull { c -> c.substringAfterLast(if (writing == Writing.DECIMAL_COMMA) ',' else '.', "").takeIf { c.any { it == ',' || it == '.' } }?.length }.maxOrNull().let { it ?: 0 })
        FieldType.CHOICE -> {
            val options = cells.flatMap { if (writing == Writing.OPTIONS_SEMICOLON) it.split(';') else listOf(it) }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            mapOf("options" to options.map { mapOf("value" to it) }) + (if (writing == Writing.OPTIONS_SEMICOLON || writing == Writing.OPTIONS_COMMA) mapOf("multiple" to true) else emptyMap())
        }
        FieldType.SCALE -> mapOf("min" to 0, "max" to 10)
        else -> null
    }

    /**
     * What is missing from [declarations] to import [table], in words ([text], the shared strings):
     * a column not declared, a field or a writing not said, a key where names do not find entries,
     * no column for the name where the tool requires one ([nameRequired]). Empty when complete.
     */
    fun missing(table: ImportTable, declarations: List<ColumnDeclaration>, fields: Map<String, FieldDefinition>, uniqueName: Boolean, nameRequired: Boolean, text: (String) -> String): List<String> = buildList {
        val declared = declarations.associateBy { it.column }
        table.columns.filter { it !in declared }.forEach { add(text("import_missing_column").format(it)) }
        declarations.filter { it.column !in table.columns }.forEach { add(text("import_missing_in_file").format(it.column)) }
        declarations.forEach { d ->
            when (d.target) {
                ColumnTarget.KEY -> if (!uniqueName) add(text("import_missing_key").format(d.column))
                ColumnTarget.FIELD -> {
                    val field = d.field?.let { fields[it] }
                    if (field == null) add(text("import_missing_field").format(d.column, d.field ?: "", fields.keys.joinToString(", ")))
                    else if (d.writing == null || d.writing.type != field.type) add(text("import_missing_writing").format(d.column, field.type.name, Writing.of(field.type).joinToString(", ")))
                }
                ColumnTarget.NEW -> when {
                    d.newField == null -> add(text("import_missing_new_field").format(d.column))
                    d.writing == null || d.writing.type != d.newField.type -> add(text("import_missing_writing").format(d.column, d.newField.type.name, Writing.of(d.newField.type).joinToString(", ")))
                }
                ColumnTarget.IGNORE -> {}
            }
        }
        if (declarations.count { it.target == ColumnTarget.KEY } > 1) add(text("import_missing_one_key"))
        // Every line would be refused for its name: said once here, before any is written
        if (nameRequired && declarations.none { it.target == ColumnTarget.KEY || (it.target == ColumnTarget.FIELD && it.field == "name") }) {
            // The way a column gives the name depends on the tool: its key, or its field "name"
            add(text(if (uniqueName) "import_missing_name_key" else "import_missing_name_field"))
        }
    }

    /** The keys the file holds twice or more, the case and the spaces around not counted. */
    fun duplicateKeys(table: ImportTable, declarations: List<ColumnDeclaration>): List<String> {
        val key = declarations.firstOrNull { it.target == ColumnTarget.KEY } ?: return emptyList()
        return table.cells(table.columns.indexOf(key.column)).filter { it.isNotBlank() }
            .groupBy { CoreFields.uniqueKey(it) }.filterValues { it.size > 1 }.map { it.value.first() }
    }

    /**
     * Each line read with the declared writings: an empty cell is no answer, a cell that does not
     * read refuses its line with the reason, and the other lines go through.
     */
    fun plan(table: ImportTable, declarations: List<ColumnDeclaration>, zone: ZoneId): Plan {
        val lines = mutableListOf<PlannedLine>()
        val refused = mutableListOf<LineRefusal>()
        table.rows.forEachIndexed { i, row ->
            val line = i + 1
            var name: String? = null
            val values = mutableMapOf<String, Any>()
            val newValues = mutableMapOf<String, Any>()
            var refusal: LineRefusal? = null
            for (declaration in declarations) {
                val cell = row.getOrElse(table.columns.indexOf(declaration.column)) { "" }
                if (cell.isBlank() || declaration.target == ColumnTarget.IGNORE) continue
                if (declaration.target == ColumnTarget.KEY) { name = cell.trim(); continue }
                when (val read = declaration.writing!!.read(cell, zone)) {
                    is CellRead.Unreadable -> { refusal = LineRefusal(line, declaration.column, cell, read.reason); break }
                    is CellRead.Value -> when (declaration.target) {
                        ColumnTarget.FIELD -> if (declaration.field == "name") name = read.value.toString() else values[declaration.field!!] = read.value
                        ColumnTarget.NEW -> newValues[declaration.column] = read.value
                        else -> {}
                    }
                }
            }
            if (refusal != null) refused.add(refusal) else lines.add(PlannedLine(line, name, values, newValues))
        }
        return Plan(lines, refused)
    }

    /** Declarations from their JSON. */
    fun declarationsOf(array: JSONArray): List<ColumnDeclaration> = (0 until array.length()).map { ColumnDeclaration.fromJson(array.getJSONObject(it)) }
}
