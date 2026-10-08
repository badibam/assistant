package app.treelune.core.fields

import app.treelune.core.ai.prompts.ModelValues
import app.treelune.core.ai.prompts.SchemaNotation
import app.treelune.core.fields.migration.FieldChange
import app.treelune.core.fields.migration.FieldConfigComparator
import app.treelune.core.fields.migration.FieldDataMigrator
import app.treelune.core.fields.migration.MigrationStrategy
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import java.time.ZoneId

/**
 * A REFERENCE field: a value that names another thing by its kind and id, what its config
 * accepts, what narrowing it does to the entries, how it is filtered, and how the model reads
 * and writes it -- with the current name, sent back without it.
 */
class ReferenceFieldTest {

    private val foods = Reference(ReferenceKind.TOOL_INSTANCE, "foods")
    private val apple = Reference(ReferenceKind.ENTRY, "apple")
    private val appleValue = mapOf("kind" to "ENTRY", "id" to "apple")

    private fun config(kinds: List<String>, tools: List<String> = emptyList()): Map<String, Any> = mapOf(
        "target" to mapOf("kinds" to kinds, "tool_instances" to tools.map { mapOf("kind" to "TOOL_INSTANCE", "id" to it) })
    )

    private fun field(config: Map<String, Any>, name: String = "food") =
        FieldDefinition(name = name, displayName = name, description = null, type = FieldType.REFERENCE, alwaysVisible = false, config = config)

    @Test
    fun `a target reads its kinds and the tools its entries come from`() {
        val target = ReferenceTarget.fromConfig(config(listOf("ENTRY"), listOf("foods", "recipes")))
        assertEquals(setOf(ReferenceKind.ENTRY), target.kinds)
        assertEquals(listOf("foods", "recipes"), target.toolInstances)
        assertTrue(target.acceptsEntryOf("recipes"))
        assertFalse(target.acceptsEntryOf("meals"))
        assertTrue(ReferenceTarget.fromConfig(config(listOf("ENTRY"))).acceptsEntryOf("meals"))
    }

    @Test
    fun `a value is a kind and an id, the app alone having none`() {
        assertEquals(apple, ReferenceTarget.referenceOf(appleValue))
        assertEquals(apple, ReferenceTarget.referenceOf(JSONObject("""{"kind":"ENTRY","id":"apple"}""")))
        assertEquals(Reference(ReferenceKind.APP, null), ReferenceTarget.referenceOf(mapOf("kind" to "APP")))
        assertNull(ReferenceTarget.referenceOf(mapOf("kind" to "ENTRY")))
        assertNull(ReferenceTarget.referenceOf(mapOf("kind" to "APP", "id" to "x")))
        assertNull(ReferenceTarget.referenceOf("apple"))
    }

    @Test
    fun `the value schema takes the kinds of the target and nothing but kind and id`() {
        val schema = FieldValueSchema.of(field(config(listOf("ENTRY", "ZONE"))))
        assertEquals("object", schema.getString("type"))
        assertEquals(JSONArray(listOf("ENTRY", "ZONE")).toString(), schema.getJSONObject("properties").getJSONObject("kind").getJSONArray("enum").toString())
        assertFalse(schema.getBoolean("additionalProperties"))
        assertEquals(FieldValueSchema.REFERENCE, schema.getString("format"))
    }

    // Migration: what a narrowed target does to the stored values

    private val instances = mapOf("apple" to "foods", "cake" to "recipes")

    private fun narrowed(values: Map<String, Any?>, newConfig: Map<String, Any>): Map<String, Any?> {
        val change = FieldChange.ReferenceTargetNarrowed("food", newConfig)
        return FieldDataMigrator.applyMigrationStrategies(values, listOf(change), mapOf(change to MigrationStrategy.STRIP_FIELD_IF_VALUE)) { instances[it] }
    }

    @Test
    fun `changing what a reference takes is a change of its own`() {
        val changes = FieldConfigComparator.compare(listOf(field(config(listOf("ENTRY"), listOf("foods", "recipes")))), listOf(field(config(listOf("ENTRY"), listOf("foods")))))
        assertTrue(changes.single() is FieldChange.ReferenceTargetNarrowed)
    }

    @Test
    fun `an entry of a tool no longer taken loses the value, one still taken keeps it`() {
        val onlyFoods = config(listOf("ENTRY"), listOf("foods"))
        assertEquals(appleValue, narrowed(mapOf("food" to appleValue), onlyFoods)["food"])
        assertFalse(narrowed(mapOf("food" to mapOf("kind" to "ENTRY", "id" to "cake")), onlyFoods).containsKey("food"))
    }

    @Test
    fun `a kind no longer taken loses the value, a deleted entry keeps its reference`() {
        assertFalse(narrowed(mapOf("food" to appleValue), config(listOf("ZONE"))).containsKey("food"))
        val gone = mapOf("kind" to "ENTRY", "id" to "gone")
        assertEquals(gone, narrowed(mapOf("food" to gone), config(listOf("ENTRY"), listOf("foods")))["food"])
    }

    // Filters: a reference is compared by the id it holds

    private lateinit var db: Connection

    @Before
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        db.createStatement().execute(
            "CREATE TABLE tool_data (id TEXT, tool_instance_id TEXT, tooltype TEXT, timestamp INTEGER, name TEXT, " +
                "data TEXT, created_at INTEGER, updated_at INTEGER, extra TEXT, state TEXT)"
        )
        listOf(
            Triple("m1", 1L, """{"food": {"kind": "ENTRY", "id": "apple"}}"""),
            Triple("m2", 2L, """{"food": {"kind": "ENTRY", "id": "cake"}}"""),
            Triple("m3", 3L, null)
        ).forEach { (id, timestamp, extra) ->
            db.prepareStatement("INSERT INTO tool_data VALUES (?, 'meals', 'tracking', ?, NULL, '{}', 0, 0, ?, NULL)").apply {
                setString(1, id); setLong(2, timestamp); setString(3, extra); executeUpdate()
            }
        }
    }

    @After
    fun tearDown() = db.close()

    private val fields = mapOf("extra.food" to field(config(listOf("ENTRY"))))

    private fun ids(json: String): List<String> {
        val filters = (EntryFilters.parse(JSONArray(json), fields) { it } as EntryFilters.Parsed.Ready).filters
        val query = EntryFilters.select("meals", filters, fields, null, 0)
        val statement = db.prepareStatement(query.clause)
        query.args.forEachIndexed { i, arg -> statement.setObject(i + 1, arg) }
        val rows = statement.executeQuery()
        return buildList { while (rows.next()) add(rows.getString(1)) }
    }

    @Test
    fun `a reference filters by what it designates, or by having one`() {
        assertEquals(listOf("m1"), ids("""[{"left": {"field": "extra.food"}, "op": "=", "right": {"constant": {"kind": "ENTRY", "id": "apple"}}}]"""))
        assertEquals(listOf("m3"), ids("""[{"left": {"field": "extra.food"}, "op": "absent"}]"""))
        assertTrue(EntryFilters.parse(JSONArray("""[{"left": {"field": "extra.food"}, "op": ">", "right": {"constant": {"kind": "ENTRY", "id": "apple"}}}]"""), fields) { it } is EntryFilters.Parsed.Refused)
    }

    // The model: a reference read with its current name, written back without it

    private val entrySchema = JSONObject().put("type", "object").put("properties", JSONObject()
        .put("extra", JSONObject().put("type", "object").put("properties", JSONObject().put("food", FieldValueSchema.of(field(config(listOf("ENTRY"))))))))

    @Test
    fun `the model reads a reference with the name of what it designates, or that it was deleted`() {
        val entry = mapOf("extra" to mapOf("food" to appleValue))
        assertEquals(listOf(apple), ModelValues.references(entry, entrySchema))
        val named = ModelValues.withReferenceNames(entry, entrySchema, mapOf(apple to "Apple")) as Map<*, *>
        assertEquals(mapOf("kind" to "ENTRY", "id" to "apple", "name" to "Apple"), (named["extra"] as Map<*, *>)["food"])
        val deleted = ModelValues.withReferenceNames(entry, entrySchema, mapOf(apple to null)) as Map<*, *>
        assertEquals(true, ((deleted["extra"] as Map<*, *>)["food"] as Map<*, *>)["deleted"])
    }

    @Test
    fun `a reference sent back as read is stored without its name`() {
        val sent = mapOf("extra" to mapOf("food" to mapOf("kind" to "ENTRY", "id" to "apple", "name" to "Apple")))
        val stored = ModelValues.fromModel(sent, entrySchema, ZoneId.of("Europe/Paris")) as Map<*, *>
        assertEquals(appleValue, (stored["extra"] as Map<*, *>)["food"])
    }

    @Test
    fun `the notation writes a reference as one value with its kinds`() {
        val notation = SchemaNotation.render(entrySchema)
        assertTrue(notation, notation.contains("food: reference {kind: ENTRY, id}"))
    }
}
