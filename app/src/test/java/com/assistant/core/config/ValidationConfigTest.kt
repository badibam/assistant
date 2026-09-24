package com.assistant.core.config

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers how the validation settings are stored and read back. */
class ValidationConfigTest {

    /** What a fresh install writes is what the app reads back. */
    @Test
    fun theDefaultsReadBackAsWritten() {
        val stored = JSONObject(ValidationConfig().toSettingsJson())

        assertEquals(ValidationConfig(), ValidationConfig.fromSettingsJson(stored))
    }

    /** A missing flag is an error: it used to be read as "no validation" without a word. */
    @Test(expected = JSONException::class)
    fun aMissingFlagIsRefused() {
        ValidationConfig.fromSettingsJson(JSONObject().put(ValidationConfig.KEY_APP_CONFIG, true))
    }
}
