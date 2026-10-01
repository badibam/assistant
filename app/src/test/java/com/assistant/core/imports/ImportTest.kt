package com.assistant.core.imports

import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * An import read into lines of text values, each column read in a writing of its type: the
 * detection reads the whole file and does not choose between two writings that disagree; an
 * empty cell is no answer; a cell that does not read refuses its line alone.
 */
class ImportTest {

    private val zone = ZoneId.of("Europe/Paris")

    private fun field(name: String, type: FieldType, config: Map<String, Any>? = null) = FieldDefinition(name, name, null, type, false, config)

    @Test
    fun `a CSV finds its separator, keeps quoted separators and doubled quotes`() {
        val table = CsvReader.read("﻿nom;kcal;note\npomme;52;\"rouge; croquante\"\n\"pain \"\"complet\"\"\";250;\n") { it }
        assertEquals(listOf("nom", "kcal", "note"), table.columns)
        assertEquals(listOf("pomme", "52", "rouge; croquante"), table.rows[0])
        assertEquals("pain \"complet\"", table.rows[1][0])
    }

    @Test
    fun `each writing reads its own form and refuses another`() {
        assertEquals(CellRead.Value(1.5), Writing.DECIMAL_COMMA.read("1,5", zone))
        assertTrue(Writing.DECIMAL_POINT.read("1,5", zone) is CellRead.Unreadable)
        assertEquals(CellRead.Value("2026-09-03"), Writing.DAY_MONTH_YEAR.read("3/9/2026", zone))
        assertEquals(CellRead.Value(5_100_000L), Writing.DURATION_COMPACT.read("1h25", zone))
        assertEquals(CellRead.Value(5_100_000L), Writing.DURATION_MINUTES.read("85 min", zone))
        assertEquals(CellRead.Value(listOf("a", "b")), Writing.OPTIONS_SEMICOLON.read("a; b", zone))
        assertTrue(Writing.HOURS_MINUTES.read("25:00", zone) is CellRead.Unreadable)
    }

    @Test
    fun `a single unambiguous day settles day and month for the whole column`() {
        val table = ImportTable(listOf("jour"), listOf(listOf("03/04/2026"), listOf("28/09/2026")))
        val proposal = ImportPlanner.detect(table, emptyMap(), uniqueName = false, zone).single()
        assertEquals(Writing.DAY_MONTH_YEAR, proposal.declaration.writing)
        assertEquals(FieldType.DATE, proposal.declaration.newField?.type)
        assertEquals("28/09/2026" to "2026-09-28", proposal.example)
    }

    @Test
    fun `two writings that read everything differently are left to choose`() {
        val table = ImportTable(listOf("jour"), listOf(listOf("03/04/2026"), listOf("05/06/2026")))
        val proposal = ImportPlanner.detect(table, emptyMap(), uniqueName = false, zone).single()
        assertNull(proposal.declaration.writing)
        assertTrue(Writing.DAY_MONTH_YEAR in proposal.ambiguous && Writing.MONTH_DAY_YEAR in proposal.ambiguous)
    }

    @Test
    fun `the name is the key where names are unique, a known field is filled, a header can fix a type`() {
        val table = ImportTable(listOf("Nom", "kcal_100g", "portion [NUMERIC]"), listOf(listOf("pomme", "52", "1"), listOf("pain", "250", "2")))
        val fields = mapOf("name" to field("name", FieldType.TEXT).copy(displayName = "Nom"), "extra.kcal_100g" to field("kcal_100g", FieldType.NUMERIC))
        val proposals = ImportPlanner.detect(table, fields, uniqueName = true, zone)
        assertEquals(ColumnTarget.KEY, proposals[0].declaration.target)
        assertEquals("extra.kcal_100g", proposals[1].declaration.field)
        assertEquals(FieldType.NUMERIC, proposals[2].declaration.newField?.type)
        assertEquals("portion", proposals[2].declaration.newField?.displayName)
    }

    @Test
    fun `a declaration misses nothing, or says what it misses`() {
        val table = ImportTable(listOf("a", "b"), emptyList())
        val missing = ImportPlanner.missing(table, listOf(ColumnDeclaration("a", ColumnTarget.KEY)), emptyMap(), uniqueName = false, nameRequired = false) { "$it %1\$s" }
        assertTrue(missing.contains("import_missing_column b"))
        assertTrue(missing.contains("import_missing_key a"))
    }

    @Test
    fun `a tool that requires a name refuses a declaration with no column for it, saying how to give it`() {
        val table = ImportTable(listOf("a"), emptyList())
        val ignored = listOf(ColumnDeclaration("a", ColumnTarget.IGNORE))
        fun missing(declarations: List<ColumnDeclaration>, uniqueName: Boolean, nameRequired: Boolean) =
            ImportPlanner.missing(table, declarations, emptyMap(), uniqueName, nameRequired) { it }
        assertTrue(missing(ignored, uniqueName = true, nameRequired = true).contains("import_missing_name_key"))
        assertTrue(missing(ignored, uniqueName = false, nameRequired = true).contains("import_missing_name_field"))
        assertFalse(missing(listOf(ColumnDeclaration("a", ColumnTarget.KEY)), uniqueName = true, nameRequired = true).any { it.startsWith("import_missing_name") })
        assertFalse(missing(ignored, uniqueName = true, nameRequired = false).any { it.startsWith("import_missing_name") })
    }

    @Test
    fun `a key twice in the file is named`() {
        val table = ImportTable(listOf("nom"), listOf(listOf("Pomme"), listOf("pomme "), listOf("pain")))
        assertEquals(listOf("Pomme"), ImportPlanner.duplicateKeys(table, listOf(ColumnDeclaration("nom", ColumnTarget.KEY))))
    }

    @Test
    fun `an empty cell is no answer, an unreadable one refuses its line and the others go through`() {
        val table = ImportTable(listOf("nom", "kcal"), listOf(listOf("pomme", "52"), listOf("pain", ""), listOf("riz", "beaucoup")))
        val plan = ImportPlanner.plan(table, listOf(
            ColumnDeclaration("nom", ColumnTarget.KEY),
            ColumnDeclaration("kcal", ColumnTarget.FIELD, field = "extra.kcal", writing = Writing.DECIMAL_POINT)
        ), zone)
        assertEquals(listOf(1, 2), plan.lines.map { it.line })
        assertEquals(mapOf("extra.kcal" to 52.0), plan.lines[0].values)
        assertEquals(emptyMap<String, Any>(), plan.lines[1].values)
        assertEquals(3, plan.refused.single().line)
    }
}
