package app.treelune.core.fields.settings

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The problems the form judges alone (docs/design/settings-pages.md): what marks a page's line, and
 * every line above it, since a page holds those under it.
 */
class SettingProblemsTest {

    private fun field(name: String, type: FieldType = FieldType.TEXT, required: Boolean = false, default: Any? = null,
                      config: Map<String, Any>? = null, systemWritten: Boolean = false) =
        SettingNode.Field(FieldDefinition(name, name, null, type, false, config), required = required, default = default, systemWritten = systemWritten)

    private fun problem(nodes: List<SettingNode>, config: String) = SettingProblems.any(nodes, JSONObject(config))

    @Test
    fun aRequiredSettingEmpty_isAProblem_unlessItHasADefaultOrTheAppWritesIt() {
        val nodes = listOf(field("name", required = true))
        assertTrue(problem(nodes, "{}"))
        assertTrue(problem(nodes, """{"name": "  "}"""))
        assertFalse(problem(nodes, """{"name": "Run"}"""))
        assertFalse(problem(listOf(field("mode", required = true, default = "a")), "{}"))
        assertFalse(problem(listOf(field("key", required = true, systemWritten = true)), "{}"))
        assertFalse(problem(listOf(field("note")), "{}"))
    }

    @Test
    fun aNumberOutOfItsBounds_isAProblem() {
        val nodes = listOf(field("repeat", FieldType.NUMERIC, config = mapOf("min" to 1, "max" to 10)))
        assertTrue(problem(nodes, """{"repeat": 0}"""))
        assertTrue(problem(nodes, """{"repeat": 11}"""))
        assertFalse(problem(nodes, """{"repeat": 5}"""))
    }

    @Test
    fun aProblemDeepInAListElement_marksEveryLevelAbove() {
        val step = listOf(field("name", required = true))
        val block = SettingNode.Group("block", "block", listOf(
            SettingNode.ListOf("steps", "steps", SettingNode.Item.Of(step), summary = listOf("name"))
        ))
        assertTrue(problem(listOf(block), """{"block": {"steps": [{"name": "A"}, {}]}}"""))
        assertFalse(problem(listOf(block), """{"block": {"steps": [{"name": "A"}]}}"""))
    }

    @Test
    fun aListUnderItsFewestElements_isAProblem() {
        val list = SettingNode.ListOf("steps", "steps", SettingNode.Item.Value(FieldDefinition("v", "v", null, FieldType.TEXT, false, null)), required = true, minItems = 2)
        assertTrue(problem(listOf(list), """{"steps": ["a"]}"""))
        assertFalse(problem(listOf(list), """{"steps": ["a", "b"]}"""))
    }

    @Test
    fun onlyTheVariantChosen_counts() {
        val variant = SettingNode.Variant(field("end", required = true, default = "manual"), mapOf(
            "manual" to emptyList(),
            "timed" to listOf(field("duration", FieldType.DURATION, required = true))
        ))
        assertFalse(problem(listOf(variant), """{"end": "manual"}"""))
        assertTrue(problem(listOf(variant), """{"end": "timed"}"""))
    }

    @Test
    fun aConditionHalfWritten_isAProblem() {
        val condition = SettingNode.Condition("condition", "condition", "end", null, required = true)
        assertTrue(problem(listOf(condition), "{}"))
        // Its operator left empty (a goal's criterion refused for it on saving)
        assertTrue(problem(listOf(condition), """{"condition": {"left": {"constant": 3}}}"""))
        assertTrue(problem(listOf(condition), """{"condition": {"left": {"variable": "x"}, "op": ">", "right": {"constant": null}}}"""))
        assertFalse(problem(listOf(condition), """{"condition": {"left": {"variable": "x"}, "op": ">", "right": {"constant": 2100}}}"""))
        assertFalse(problem(listOf(condition), """{"condition": {"left": {"variable": "x"}, "op": "absent"}}"""))
        assertTrue(problem(listOf(condition), """{"condition": {"left": {"variable": "x"}, "op": "between", "right": [{"constant": 1}]}}"""))
    }

    @Test
    fun aConditionPutOnTheEntry_needsNoLeftOfItsOwn() {
        val nodes = listOf(
            SettingNode.Group("entered", "entered", listOf(field("type", required = true))),
            SettingNode.Condition("condition", "condition", "end", null, required = true, enteredField = "entered")
        )
        assertFalse(problem(nodes, """{"entered": {"type": "NUMERIC"}, "condition": {"op": ">", "right": {"constant": 5}}}"""))
        // Without the entered value declared, its left is a term to write
        assertTrue(problem(nodes, """{"condition": {"op": ">", "right": {"constant": 5}}}"""))
    }

    @Test
    fun aTermWithoutItsValue_isAProblem() {
        val term = SettingNode.Term("target", "target", app.treelune.core.terms.Term.Kind.entries.toSet(), "end", null, required = true)
        assertTrue(problem(listOf(term), """{"target": {"constant": null}}"""))
        assertFalse(problem(listOf(term), """{"target": {"constant": 8}}"""))
    }

    @Test
    fun aRequiredGroupMissing_isAProblem() {
        val group = SettingNode.Group("config", "config", listOf(field("unit")), required = true)
        assertTrue(problem(listOf(group), "{}"))
        assertFalse(problem(listOf(group), """{"config": {}}"""))
        // An optional list may stay empty
        assertFalse(problem(listOf(SettingNode.ListOf("tags", "tags", SettingNode.Item.Value(FieldDefinition("v", "v", null, FieldType.TEXT, false, null)))), """{"tags": []}"""))
    }
}
