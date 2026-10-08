package app.treelune.core.versioning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the rewrite the 31 -> 32 migration and the backup import both apply to ai_limits: the
 * CHAT limit becomes 10, the automation limit is kept, and every other key goes.
 */
class AILimitsAtV32Test {

    /** The settings a fresh install wrote before v32: dead keys beside the two limits. */
    @Test
    fun formerDefaults_keepTheTwoLimitsOnly() {
        val former = JSONObject("""
            {
                "default_query_max_tokens": 2000,
                "default_chars_per_token": 4.5,
                "default_prompt_max_tokens": 15000,
                "chat_max_data_query_iterations": 3,
                "chat_max_action_retries": 3,
                "chat_max_autonomous_roundtrips": 10,
                "automation_max_data_query_iterations": 5,
                "automation_max_action_retries": 5,
                "automation_max_autonomous_roundtrips": 20
            }
        """)

        val rewritten = AILimitsAtV32.rewrite(former)

        assertEquals(setOf("chat_max_autonomous_roundtrips", "automation_max_autonomous_roundtrips"), rewritten.keys().asSequence().toSet())
        assertEquals(10, rewritten.getInt("chat_max_autonomous_roundtrips"))
        assertEquals(20, rewritten.getInt("automation_max_autonomous_roundtrips"))
    }

    /** A CHAT with no limit stored, which the code used to read as unlimited, gets 10. */
    @Test
    fun aMissingChatLimit_becomesTen() {
        val rewritten = AILimitsAtV32.rewrite(JSONObject().put("automation_max_autonomous_roundtrips", 20))

        assertEquals(10, rewritten.getInt("chat_max_autonomous_roundtrips"))
    }

    /** No screen could set the CHAT limit, so any stored value was a default and is replaced. */
    @Test
    fun anUnlimitedChat_becomesTen() {
        val rewritten = AILimitsAtV32.rewrite(JSONObject().put("chat_max_autonomous_roundtrips", Int.MAX_VALUE))

        assertEquals(10, rewritten.getInt("chat_max_autonomous_roundtrips"))
    }

    /** The automation limit is kept as stored, and set to 20 only when absent. */
    @Test
    fun theAutomationLimit_isKeptOrSetToTwenty() {
        val kept = AILimitsAtV32.rewrite(JSONObject().put("automation_max_autonomous_roundtrips", 7))
        val absent = AILimitsAtV32.rewrite(JSONObject())

        assertEquals(7, kept.getInt("automation_max_autonomous_roundtrips"))
        assertEquals(20, absent.getInt("automation_max_autonomous_roundtrips"))
    }
}
