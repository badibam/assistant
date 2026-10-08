package app.treelune.core.ai.providers

import app.treelune.core.ai.data.MessageSender
import app.treelune.core.ai.data.PromptData
import app.treelune.core.ai.data.SessionMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the request sent to an OpenAI-compatible server (Chat Completions), the reading of its
 * answer, and the address it may be sent to.
 *
 * What was promised: one system message first, since some chat templates refuse a second one;
 * the output forcing asked for exactly as chosen, never lowered; an answer cut by the length
 * limit refused; the usage read so that a paid call never shows as free; plain http refused,
 * since the prompt carries the user's data.
 */
class OpenAICompatibleExtensionsTest {

    private fun message(sender: MessageSender, text: String? = null, aiJson: String? = null) = SessionMessage(
        id = "m",
        timestamp = 0L,
        sender = sender,
        richContent = null,
        textContent = text,
        aiMessage = null,
        aiMessageJson = aiJson,
        systemMessage = null
    )

    private val schema = buildJsonObject { put("type", "object") }

    private fun request(forcing: OutputForcing = OutputForcing.NONE, level2: String = "L2 user data", vararg history: SessionMessage): JsonObject =
        PromptData(
            level1Content = "L1 documentation",
            level2Content = level2,
            level3Content = "L3 app state",
            sessionMessages = history.toList()
        ).toChatCompletionsJson("model-test", 1.0, 1000, forcing, if (forcing == OutputForcing.SCHEMA) schema else null,
            "Current date and time: 2026-10-01T10:00:00+02:00")

    private fun JsonObject.messages() = this["messages"]!!.jsonArray.map { it.jsonObject }

    // ==================== The request ====================

    /** The levels make one system message, first; the conversation follows, the dated message last. */
    @Test
    fun oneSystemMessage_leads_andTheDatedMessageCloses() {
        val messages = request(
            OutputForcing.NONE, "L2 user data",
            message(MessageSender.USER, text = "q1"),
            message(MessageSender.AI, aiJson = """{"pre_text":"a1"}"""),
            message(MessageSender.USER, text = "q2")
        ).messages()

        assertEquals(listOf("system", "user", "assistant", "user", "user"), messages.map { it["role"]!!.jsonPrimitive.content })
        assertEquals("L1 documentation\n\nL2 user data\n\nL3 app state", messages[0]["content"]!!.jsonPrimitive.content)
        assertEquals("""{"pre_text":"a1"}""", messages[2]["content"]!!.jsonPrimitive.content)
        assertEquals("Current date and time: 2026-10-01T10:00:00+02:00", messages.last()["content"]!!.jsonPrimitive.content)
    }

    /** An empty level leaves no blank paragraph in the system message. */
    @Test
    fun anEmptyLevel_isLeftOutOfTheSystemMessage() {
        val system = request(OutputForcing.NONE, "").messages()[0]["content"]!!.jsonPrimitive.content

        assertEquals("L1 documentation\n\nL3 app state", system)
    }

    @Test
    fun noForcing_asksForNoFormat() {
        assertFalse(request(OutputForcing.NONE).containsKey("response_format"))
    }

    @Test
    fun jsonForcing_asksForAJsonObject() {
        assertEquals("json_object", request(OutputForcing.JSON)["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    /** The exact schema goes as given, under the name of the AI's answer. */
    @Test
    fun schemaForcing_sendsTheSchema() {
        val format = request(OutputForcing.SCHEMA)["response_format"]!!.jsonObject

        assertEquals("json_schema", format["type"]!!.jsonPrimitive.content)
        assertEquals("ai_message_response", format["json_schema"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(schema, format["json_schema"]!!.jsonObject["schema"])
    }

    @Test
    fun forcingLevels_readFromTheirStoredValue() {
        assertEquals(OutputForcing.SCHEMA, OutputForcing.of("schema"))
        assertEquals(listOf("none", "json", "schema"), OutputForcing.entries.map { it.stored })
    }

    // ==================== The address ====================

    @Test
    fun onlyHttpsAddresses_areCalled() {
        assertTrue(isHttpsAddress("https://openrouter.ai/api/v1"))
        assertTrue(isHttpsAddress("https://box.tail1234.ts.net/v1"))
        assertFalse(isHttpsAddress("http://192.168.1.10:11434/v1"))
        assertFalse(isHttpsAddress("http://10.0.2.2:11434/v1"))
        assertFalse(isHttpsAddress("openrouter.ai/api/v1"))
        assertFalse(isHttpsAddress(""))
    }

    @Test
    fun endpoints_joinTheAddress_whateverItsTrailingSlash() {
        assertEquals("https://h/v1/chat/completions", endpointOf("https://h/v1", "chat/completions"))
        assertEquals("https://h/v1/models", endpointOf("https://h/v1/", "models"))
    }

    // ==================== Reading the answer ====================

    private fun answer(json: String) = Json.parseToJsonElement(json).toChatCompletionsResponse()

    /** prompt_tokens includes the cached ones: the app's input count is the uncached rest. */
    @Test
    fun aCompletedAnswer_givesItsText_andTheUncachedInput() {
        val response = answer("""
            {"choices": [{"message": {"role": "assistant", "content": "{\"pre_text\":\"ok\"}"}, "finish_reason": "stop"}],
             "usage": {"prompt_tokens": 1000, "completion_tokens": 50, "prompt_tokens_details": {"cached_tokens": 800}}}
        """)

        assertTrue(response.success)
        assertEquals("""{"pre_text":"ok"}""", response.content)
        assertEquals(200, response.inputTokens)
        assertEquals(800, response.cacheReadTokens)
        assertEquals(50, response.tokensUsed)
    }

    /** A server that reports no cache read none. */
    @Test
    fun noCacheDetails_meansNothingCached() {
        val response = answer("""
            {"choices": [{"message": {"content": "{}"}, "finish_reason": "stop"}], "usage": {"prompt_tokens": 10, "completion_tokens": 2}}
        """)

        assertEquals(10, response.inputTokens)
        assertEquals(0, response.cacheReadTokens)
    }

    /** A JSON cut by the length limit is unusable: refused, never passed on. */
    @Test
    fun anAnswerCutByTheLimit_isRefused() {
        val response = answer("""
            {"choices": [{"message": {"content": "{\"pre_text\":"}, "finish_reason": "length"}], "usage": {"prompt_tokens": 10, "completion_tokens": 2}}
        """)

        assertFalse(response.success)
        assertEquals(AIFailure.CONFIG, response.failure)
    }

    /** Without its counts, a paid call would show as free. */
    @Test
    fun anAnswerWithoutUsage_isRefused() {
        val response = answer("""{"choices": [{"message": {"content": "{}"}, "finish_reason": "stop"}]}""")

        assertFalse(response.success)
    }

    /** An error in a 200 body is a refusal, its message kept. */
    @Test
    fun anErrorInTheBody_isARefusal() {
        val response = answer("""{"error": {"message": "model not found", "code": 404}}""")

        assertFalse(response.success)
        assertEquals(AIFailure.REFUSED, response.failure)
        assertEquals("model not found", response.errorMessage)
    }

    @Test
    fun anAnswerWithoutContent_isRefused() {
        val response = answer("""
            {"choices": [{"message": {"content": null}, "finish_reason": "stop"}], "usage": {"prompt_tokens": 10, "completion_tokens": 2}}
        """)

        assertFalse(response.success)
        assertNull(response.content.takeIf { it.isNotEmpty() })
    }
}
