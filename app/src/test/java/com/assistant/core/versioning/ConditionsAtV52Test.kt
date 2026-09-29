package com.assistant.core.versioning

import com.assistant.core.conditions.Conditions
import com.assistant.core.fields.EntryFilters
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A filter stored before v52 reads again as a condition, wherever it is stored: a pointer or a
 * variable whose filters no longer read would stop narrowing, or stop reading, without a word.
 */
class ConditionsAtV52Test {

    private val fields = mapOf(
        "data.weight" to FieldDefinition("weight", "Weight", null, FieldType.NUMERIC, false, null),
        "extra.mood" to FieldDefinition("mood", "Mood", null, FieldType.TEXT, false, null)
    )

    private fun parses(filters: JSONArray) = EntryFilters.parse(filters, fields) { it } is EntryFilters.Parsed.Ready

    @Test
    fun eachShape_becomesAConditionTheParserTakes() {
        val old = listOf(
            """{"field": "data.weight", "op": ">", "value": 80}""",
            """{"field": "data.weight", "op": "between", "value": [70, 80]}""",
            """{"field": "extra.mood", "op": "present"}"""
        )
        for (json in old) {
            val condition = ConditionsAtV52.filter(JSONObject(json))!!
            assertTrue(json, parses(JSONArray().put(condition)))
        }
        val between = ConditionsAtV52.filter(JSONObject(old[1]))!!
        assertEquals(80, (Conditions.right(between) as Conditions.Right.Written).value.let { (it as JSONArray).getInt(1) })
    }

    @Test
    fun aCondition_isLeftAsItIs() {
        assertNull(ConditionsAtV52.filter(Conditions.onField("data.weight", ">", 80)))
    }

    @Test
    fun thePointersOfAMessage_andTheTermsOfAVariable_areRewritten() {
        val selection = """{"target": {"kind": "TOOL_INSTANCE", "id": "t1"}, "filters": [{"field": "data.weight", "op": ">", "value": 80}]}"""
        val config = JSONObject().put("selection", JSONObject(selection)).toString()
        val content = JSONObject().put("segments", JSONArray().put(JSONObject()
            .put("type", "enrichment").put("enrichment_type", "POINTER").put("config", config)))
        val message = JSONObject(ConditionsAtV52.richContent(content.toString())!!)
        val pointer = JSONObject(message.getJSONArray("segments").getJSONObject(0).getString("config"))
        assertTrue(parses(pointer.getJSONObject("selection").getJSONArray("filters")))

        val definition = """{"kind": "FORMULA", "formula": "t", "terms": {"t": {"reading": {"selection": $selection, "reduction": "COUNT"}}}}"""
        val variable = JSONObject(ConditionsAtV52.definition(definition)!!)
        assertTrue(parses(variable.getJSONObject("terms").getJSONObject("t").getJSONObject("reading").getJSONObject("selection").getJSONArray("filters")))
        // Nothing to rewrite the second time
        assertNull(ConditionsAtV52.definition(variable.toString()))
    }
}
