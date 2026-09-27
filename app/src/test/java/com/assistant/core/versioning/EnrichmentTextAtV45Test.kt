package com.assistant.core.versioning

import com.assistant.core.ai.data.EnrichmentType
import com.assistant.core.ai.data.MessageSegment
import com.assistant.core.ai.data.RichMessage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A message stored before v45 keeps its text and its blocks, and loses the texts written when
 * it was sent, which named a pointer's target as it was called that day.
 */
class EnrichmentTextAtV45Test {

    private val pointer = """{"target":{"kind":"TOOL","id":"t1"},"attach":{"config":false,"entries":true}}"""

    private fun old() = JSONObject()
        .put("segments", JSONArray()
            .put(JSONObject().put("type", "text").put("content", "Look at this"))
            .put(JSONObject().put("type", "enrichment").put("enrichment_type", "POINTER").put("config", pointer)
                .put("preview", "Tool : Sleep, data").put("prompt_preview", "Tool : Sleep, data (id = t1)")))
        .put("linear_text", "Look at this\n[Tool : Sleep, data (id = t1)]")
        .toString()

    @Test
    fun theStoredTextsGo_theSegmentsStay() {
        val next = JSONObject(EnrichmentTextAtV45.richContent(old())!!)

        assertFalse(next.has("linear_text"))
        val block = next.getJSONArray("segments").getJSONObject(1)
        assertFalse(block.has("preview"))
        assertFalse(block.has("prompt_preview"))
        assertEquals(pointer, block.getString("config"))

        // And it reads as a message: its text, then its pointer as the user set it
        assertEquals(
            RichMessage(listOf(MessageSegment.Text("Look at this"), MessageSegment.EnrichmentBlock(EnrichmentType.POINTER, pointer))),
            RichMessage.fromJson(next.toString())
        )
    }

    @Test
    fun aMessageAtV45IsLeftAlone() {
        assertNull(EnrichmentTextAtV45.richContent(EnrichmentTextAtV45.richContent(old())!!))
    }

    @Test
    fun aBackupsMessagesAreRewritten() {
        val data = JSONObject().put("session_messages", JSONArray()
            .put(JSONObject().put("id", "m1").put("rich_content_json", old()))
            .put(JSONObject().put("id", "m2").put("rich_content_json", JSONObject.NULL)))

        EnrichmentTextAtV45.backup(data)

        val rewritten = JSONObject(data.getJSONArray("session_messages").getJSONObject(0).getString("rich_content_json"))
        assertFalse(rewritten.has("linear_text"))
    }

    @Test
    fun aMessageStoresItsSegmentsAlone() {
        val stored = JSONObject(RichMessage(listOf(MessageSegment.EnrichmentBlock(EnrichmentType.POINTER, pointer))).toJson())

        assertEquals(setOf("segments"), stored.keys().asSequence().toSet())
        assertEquals(setOf("type", "enrichment_type", "config"), stored.getJSONArray("segments").getJSONObject(0).keys().asSequence().toSet())
    }
}
