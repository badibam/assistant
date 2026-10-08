package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Covers the questionnaire entries brought to v65, where every one has its status. */
class QuestionnaireStateAtV65Test {

    /** One the AI passed in a chat, shown filled by the screen alone: filled for everyone now. */
    @Test
    fun anEntryWithoutStatus_isFilled_itsMomentLeftUnset() {
        val state = JSONObject(QuestionnaireStateAtV65.state("questionnaire", null)!!)

        assertEquals("filled", state.getString("status"))
        assertFalse(state.has("filled_at"))
        assertEquals("filled", JSONObject(QuestionnaireStateAtV65.state("questionnaire", "{}")!!).getString("status"))
    }

    @Test
    fun anEntryWithItsStatus_andOtherTools_areLeftAsTheyAre() {
        assertNull(QuestionnaireStateAtV65.state("questionnaire", """{"status":"to_fill"}"""))
        assertNull(QuestionnaireStateAtV65.state("messages", null))
    }

    @Test
    fun aBackup_isRewrittenTheSameWay() {
        val data = JSONObject().put("tool_data", JSONArray()
            .put(JSONObject().put("tooltype", "questionnaire").put("state", JSONObject.NULL))
            .put(JSONObject().put("tooltype", "questionnaire").put("state", """{"status":"ignored"}"""))
            .put(JSONObject().put("tooltype", "notes").put("state", """{"position":1}""")))

        QuestionnaireStateAtV65.backup(data)

        val entries = data.getJSONArray("tool_data")
        assertEquals("filled", JSONObject(entries.getJSONObject(0).getString("state")).getString("status"))
        assertEquals("""{"status":"ignored"}""", entries.getJSONObject(1).getString("state"))
        assertEquals("""{"position":1}""", entries.getJSONObject(2).getString("state"))
    }
}
