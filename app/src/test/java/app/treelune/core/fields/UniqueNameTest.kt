package app.treelune.core.fields

import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * A name unique in its tool (structured data): compared without the case nor the spaces around,
 * by the service that refuses a duplicate and by a filter on the name alike.
 */
class UniqueNameTest {

    private lateinit var db: Connection

    @Before
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        db.createStatement().execute(
            "CREATE TABLE tool_data (id TEXT, tool_instance_id TEXT, tooltype TEXT, timestamp INTEGER, name TEXT, " +
                "data TEXT, created_at INTEGER, updated_at INTEGER, extra TEXT, state TEXT)"
        )
        listOf("apple" to "Apple ", "bread" to "Bread").forEach { (id, name) ->
            db.prepareStatement("INSERT INTO tool_data VALUES (?, 't1', 'structured', NULL, ?, '{}', 0, 0, NULL, NULL)").apply {
                setString(1, id); setString(2, name); executeUpdate()
            }
        }
    }

    @After
    fun tearDown() = db.close()

    private fun ids(fields: Map<String, FieldDefinition>, json: String): List<String> {
        val filters = (EntryFilters.parse(JSONArray(json), fields) { it } as EntryFilters.Parsed.Ready).filters
        val query = EntryFilters.select("t1", filters, fields, null, 0)
        val statement = db.prepareStatement(query.clause)
        query.args.forEachIndexed { i, arg -> statement.setObject(i + 1, arg) }
        val rows = statement.executeQuery()
        return buildList { while (rows.next()) add(rows.getString(1)) }
    }

    @Test
    fun `the key leaves out the case and the spaces around`() {
        assertEquals(CoreFields.uniqueKey(" Apple"), CoreFields.uniqueKey("apple "))
    }

    @Test
    fun `a unique name is found the way uniqueness compares it, an ordinary one exactly`() {
        val unique = EntryFilters.filterableFields(EntryFields(nameUnique = true, timestamp = CoreFieldUsage.ABSENT), emptyList()) { it }
        assertTrue(unique.getValue("name").config?.get(CoreFields.UNIQUE) == true)
        assertEquals(listOf("apple"), ids(unique, """[{"left": {"field": "name"}, "op": "=", "right": {"constant": " apple"}}]"""))

        val ordinary = EntryFilters.filterableFields(EntryFields(), emptyList()) { it }
        assertEquals(emptyList<String>(), ids(ordinary, """[{"left": {"field": "name"}, "op": "=", "right": {"constant": "apple"}}]"""))
    }

    @Test
    fun `an entry without a date offers no filter on it`() {
        val fields = EntryFilters.filterableFields(EntryFields(nameUnique = true, timestamp = CoreFieldUsage.ABSENT), emptyList()) { it }
        assertTrue("timestamp" !in fields)
    }
}
