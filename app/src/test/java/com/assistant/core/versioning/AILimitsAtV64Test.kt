package com.assistant.core.versioning

import com.assistant.core.ai.domain.AILimitsConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Covers the rewrite the 63 -> 64 migration and the backup import both apply to ai_limits: the
 * threshold of the tools sent always joins the others, and nothing stored is changed.
 */
class AILimitsAtV64Test {

    private val v63 = JSONObject()
        .put("chat_max_autonomous_roundtrips", 10).put("automation_max_autonomous_roundtrips", 20)
        .put("chat_max_data_chars", 15_000).put("automation_max_data_chars", 100_000)

    @Test
    fun v63Settings_getTheThreshold_andReadBackWhole() {
        val rewritten = AILimitsAtV64.rewrite(v63)

        assertEquals(15_000, rewritten.getInt("always_send_max_chars"))
        assertEquals(15_000, AILimitsConfig.fromSettingsJson(rewritten).alwaysSendMaxChars)
        assertFalse(v63.has("always_send_max_chars"))
    }

    @Test
    fun aStoredThreshold_isKept() {
        val rewritten = AILimitsAtV64.rewrite(JSONObject(v63.toString()).put("always_send_max_chars", 40_000))

        assertEquals(40_000, rewritten.getInt("always_send_max_chars"))
    }

    @Test
    fun theBackupImport_appliesIt_fromV63() {
        val imported = JSONObject(JsonTransformers.transformAppConfig(v63.toString(), "ai_limits", 63, 64))

        assertEquals(15_000, imported.getInt("always_send_max_chars"))
    }
}
