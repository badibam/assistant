package com.assistant.core.fields

import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * The value filters of tool_data.get, run in a real SQLite against a tool_data table of the
 * app's shape: what each condition keeps, what counts as no answer, and what is refused.
 */
class EntryFiltersTest {

    private val text: (String) -> String = { it }
    private lateinit var db: Connection

    private fun field(name: String, type: FieldType, config: Map<String, Any>? = null) =
        FieldDefinition(name, name, null, type, false, config)

    private fun options(vararg values: String) = values.map { mapOf("value" to it) }

    private val fields = mapOf(
        "timestamp" to field("timestamp", FieldType.DATETIME),
        "name" to field("name", FieldType.TEXT),
        "data.weight" to field("weight", FieldType.NUMERIC),
        "data.sleep" to field("sleep", FieldType.DURATION),
        "data.bedtime" to field("bedtime", FieldType.TIME),
        "extra.due" to field("due", FieldType.DATE),
        "extra.note" to field("note", FieldType.TEXT),
        "extra.place" to field("place", FieldType.CHOICE, mapOf("options" to options("home", "work", "away"))),
        "extra.tags" to field("tags", FieldType.CHOICE, mapOf("options" to options("sport", "late", "sick"), "multiple" to true)),
        "extra.span" to field("span", FieldType.RANGE),
        "state.read" to field("read", FieldType.BOOLEAN)
    )

    @Before
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        db.createStatement().execute(
            "CREATE TABLE tool_data (id TEXT, tool_instance_id TEXT, tooltype TEXT, timestamp INTEGER, name TEXT, " +
                "data TEXT, created_at INTEGER, updated_at INTEGER, extra TEXT, state TEXT)"
        )
        entry("a", 1000, "Morning", """{"weight": 72.5, "sleep": 25200000, "bedtime": "22:30"}""",
            """{"due": "2026-09-10", "note": "Felt Great", "place": "home", "tags": ["sport"]}""", """{"read": true}""")
        entry("b", 2000, "Evening", """{"weight": 80, "sleep": 18000000, "bedtime": "9:05"}""",
            """{"due": "2026-09-20", "note": "", "place": "work", "tags": ["late", "sick"]}""", """{"read": false}""")
        entry("c", 3000, null, """{"weight": 76}""", """{"tags": []}""", null)
        entry("other", 2000, "Morning", """{"weight": 72.5}""", null, null, tool = "t2")
    }

    @After
    fun tearDown() = db.close()

    private fun entry(id: String, timestamp: Long, name: String?, data: String, extra: String?, state: String?, tool: String = "t1") {
        db.prepareStatement("INSERT INTO tool_data VALUES (?, ?, 'tracking', ?, ?, ?, 0, 0, ?, ?)").apply {
            setString(1, id); setString(2, tool); setLong(3, timestamp); setString(4, name)
            setString(5, data); setString(6, extra); setString(7, state)
            executeUpdate()
        }
    }

    private fun ready(json: String): List<EntryFilter> =
        (EntryFilters.parse(JSONArray(json), fields, text) as EntryFilters.Parsed.Ready).filters

    private fun refused(json: String): String =
        (EntryFilters.parse(JSONArray(json), fields, text) as EntryFilters.Parsed.Refused).error

    private fun run(query: SqlCondition): List<String> {
        val statement = db.prepareStatement(query.clause)
        query.args.forEachIndexed { i, arg -> statement.setObject(i + 1, arg) }
        val rows = statement.executeQuery()
        return buildList { while (rows.next()) add(rows.getString(1)) }
    }

    /** The ids of the entries of t1 the filters keep, newest first. */
    private fun ids(json: String, limit: Int? = null, offset: Int = 0): List<String> =
        run(EntryFilters.select("t1", ready(json), fields, limit, offset))

    private fun count(json: String): Int =
        run(EntryFilters.count("t1", ready(json), fields)).single().toInt()

    @Test
    fun `no filter keeps every entry of the tool and none of another`() {
        assertEquals(listOf("c", "b", "a"), ids("[]"))
    }

    @Test
    fun `a period is two filters on timestamp`() {
        assertEquals(listOf("b"), ids("""[{"field": "timestamp", "op": ">=", "value": 1500}, {"field": "timestamp", "op": "<=", "value": 2000}]"""))
        assertEquals(listOf("b", "a"), ids("""[{"field": "timestamp", "op": "between", "value": [1000, 2000]}]"""))
    }

    @Test
    fun `numbers and durations compare as numbers`() {
        assertEquals(listOf("c", "b"), ids("""[{"field": "data.weight", "op": ">", "value": 75}]"""))
        assertEquals(listOf("a"), ids("""[{"field": "data.weight", "op": "=", "value": 72.5}]"""))
        assertEquals(listOf("b"), ids("""[{"field": "data.sleep", "op": "<", "value": 21600000}]"""))
    }

    @Test
    fun `filters combine with AND`() {
        assertEquals(listOf("b"), ids("""[{"field": "data.weight", "op": ">", "value": 75}, {"field": "extra.place", "op": "in", "value": ["work"]}]"""))
    }

    @Test
    fun `an hour compares by time of day, whether written 9_05 or 09_05`() {
        assertEquals(listOf("b"), ids("""[{"field": "data.bedtime", "op": "<", "value": "10:00"}]"""))
        assertEquals(listOf("a"), ids("""[{"field": "data.bedtime", "op": ">=", "value": "21:00"}]"""))
    }

    @Test
    fun `a day compares as a day`() {
        assertEquals(listOf("b"), ids("""[{"field": "extra.due", "op": "between", "value": ["2026-09-15", "2026-09-30"]}]"""))
    }

    @Test
    fun `a text is matched whole or by a part, whatever the case`() {
        assertEquals(listOf("a"), ids("""[{"field": "extra.note", "op": "contains", "value": "great"}]"""))
        assertEquals(listOf("b"), ids("""[{"field": "name", "op": "=", "value": "Evening"}]"""))
    }

    @Test
    fun `a choice matches one of the options given, a multiple one by any option it holds`() {
        assertEquals(listOf("b", "a"), ids("""[{"field": "extra.place", "op": "in", "value": ["home", "work"]}]"""))
        assertEquals(listOf("b"), ids("""[{"field": "extra.tags", "op": "in", "value": ["sick", "nothing"]}]"""))
    }

    @Test
    fun `a boolean is true or false, and an entry without it is neither`() {
        assertEquals(listOf("a"), ids("""[{"field": "state.read", "op": "=", "value": true}]"""))
        assertEquals(listOf("b"), ids("""[{"field": "state.read", "op": "=", "value": false}]"""))
    }

    @Test
    fun `no answer is a missing value, an empty text or an empty list`() {
        assertEquals(listOf("c", "b"), ids("""[{"field": "extra.note", "op": "absent"}]"""))
        assertEquals(listOf("c"), ids("""[{"field": "extra.tags", "op": "absent"}]"""))
        assertEquals(listOf("c"), ids("""[{"field": "state.read", "op": "absent"}]"""))
        assertEquals(listOf("c"), ids("""[{"field": "name", "op": "absent"}]"""))
        assertEquals(listOf("b", "a"), ids("""[{"field": "extra.place", "op": "present"}]"""))
    }

    @Test
    fun `the count is that of the filtered entries, and a page is taken among them`() {
        val heavy = """[{"field": "data.weight", "op": ">", "value": 70}]"""
        assertEquals(3, count(heavy))
        assertEquals(listOf("b"), ids(heavy, limit = 1, offset = 1))
    }

    @Test
    fun `a field is compared through a bound key, never written into the SQL`() {
        val query = EntryFilters.select("t1", ready("""[{"field": "extra.note", "op": "=", "value": "x"}]"""), fields, null, 0)
        assertTrue("note" !in query.clause)
    }

    @Test
    fun `a filter on a field the entries do not have is refused, naming the ones they have`() {
        val error = refused("""[{"field": "data.mood", "op": "=", "value": 3}]""")
        assertTrue(error.startsWith("service_error_filter_unknown_field"))
    }

    @Test
    fun `a condition the field's type does not take is refused`() {
        assertTrue(refused("""[{"field": "data.weight", "op": "contains", "value": "7"}]""").startsWith("service_error_filter_operator"))
        assertTrue(refused("""[{"field": "extra.place", "op": "=", "value": "home"}]""").startsWith("service_error_filter_operator"))
        assertTrue(refused("""[{"field": "extra.span", "op": ">", "value": 3}]""").startsWith("service_error_filter_operator"))
    }

    @Test
    fun `a value that does not suit the field or the condition is refused`() {
        listOf(
            """{"field": "data.weight", "op": "<", "value": "heavy"}""",
            """{"field": "data.bedtime", "op": "<", "value": "25:00"}""",
            """{"field": "extra.due", "op": "<", "value": "15/09/2026"}""",
            """{"field": "timestamp", "op": "between", "value": [1000]}""",
            """{"field": "extra.place", "op": "in", "value": []}""",
            """{"field": "extra.note", "op": "absent", "value": "x"}"""
        ).forEach { filter ->
            assertTrue(filter, refused("[$filter]").startsWith("service_error_filter_value"))
        }
    }

    @Test
    fun `a filter that is not an object is refused`() {
        assertTrue(refused("""["timestamp > 3"]""").startsWith("service_error_filter_unreadable"))
    }

    @Test
    fun `the fields offered are the core's used ones, the tool type's, the user's and the filterable state`() {
        val declared = EntryFields(
            name = CoreFieldUsage.ABSENT,
            data = listOf(FixedField(field("value", FieldType.NUMERIC))),
            state = listOf(
                StateField(field("read", FieldType.BOOLEAN), filterable = true),
                StateField(field("position", FieldType.NUMERIC), filterable = false)
            )
        )
        val offered = EntryFilters.filterableFields(declared, listOf(field("mood", FieldType.TEXT)), text).keys
        assertEquals(setOf("timestamp", "created_at", "updated_at", "data.value", "extra.mood", "state.read"), offered)
    }
}
