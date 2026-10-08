package app.treelune.core.versioning

import app.treelune.core.ai.domain.AILimitsConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Covers the rewrite the 33 -> 34 migration and the backup import both apply to ai_limits:
 * the two data size thresholds join the roundtrip limits, and nothing stored is changed.
 */
class AILimitsAtV34Test {

    /** Settings from v33 get both thresholds, and read back as a complete config once at v64. */
    @Test
    fun v33Settings_getBothThresholds() {
        val v33 = JSONObject().put("chat_max_autonomous_roundtrips", 10).put("automation_max_autonomous_roundtrips", 20)

        val rewritten = AILimitsAtV34.rewrite(v33)

        assertEquals(15_000, rewritten.getInt("chat_max_data_chars"))
        assertEquals(100_000, rewritten.getInt("automation_max_data_chars"))
        assertEquals(10, AILimitsConfig.fromSettingsJson(AILimitsAtV64.rewrite(rewritten)).chatMaxAutonomousRoundtrips)
        assertFalse(v33.has("chat_max_data_chars"))
    }

    /** A threshold already there is kept as it was set. */
    @Test
    fun aStoredThreshold_isKept() {
        val stored = JSONObject()
            .put("chat_max_autonomous_roundtrips", 10).put("automation_max_autonomous_roundtrips", 20)
            .put("chat_max_data_chars", 40_000)

        val rewritten = AILimitsAtV34.rewrite(stored)

        assertEquals(40_000, rewritten.getInt("chat_max_data_chars"))
        assertEquals(100_000, rewritten.getInt("automation_max_data_chars"))
    }

    /** From v31, the chain gives the v32 limits, the v34 thresholds and the v64 one, which read back whole. */
    @Test
    fun v31Settings_reachACompleteConfig() {
        val v31 = """{ "chat_max_action_retries": 3, "automation_max_autonomous_roundtrips": 30 }"""

        val current = JSONObject(JsonTransformers.transformAppConfig(v31, "ai_limits", 31, 64))

        val config = AILimitsConfig.fromSettingsJson(current)
        assertEquals(30, config.automationMaxAutonomousRoundtrips)
        assertEquals(100_000, config.automationMaxDataChars)
    }
}
