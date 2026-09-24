package com.assistant.core.ai.providers

import com.assistant.core.ai.data.MessageSender
import com.assistant.core.ai.data.PromptData
import com.assistant.core.ai.data.SessionMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the request sent to the Claude Messages API and the reading of its answer.
 *
 * A mistake here costs money on every call rather than failing: a cache breakpoint in the
 * wrong place still gets an answer, only at full price, and a misread usage field makes the
 * cost screen lie. The rules checked are the API's: at most four cache_control breakpoints,
 * anything that changes on every call placed after the last one, and the usage fields
 * input_tokens (uncached only), cache_creation_input_tokens and cache_read_input_tokens.
 */
class ClaudeExtensionsTest {

    private val config = JSONObject().put("model", "claude-test").put("max_tokens", 1000)

    private fun user(text: String) = message(MessageSender.USER, text = text)
    private fun ai(json: String) = message(MessageSender.AI, aiJson = json)

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

    private fun request(vararg history: SessionMessage, effort: String? = null): JsonObject {
        val prompt = PromptData(
            level1Content = "L1 documentation",
            level2Content = "L2 user data",
            level3Content = "L3 app state",
            sessionMessages = history.toList()
        )
        val cfg = JSONObject(config.toString()).apply { if (effort != null) put("effort", effort) }
        return prompt.toClaudeJson(cfg, "Current date and time: 2026-09-24T10:00:00+02:00")
    }

    /** Every cache_control marker in the request, wherever it sits. */
    private fun breakpoints(element: JsonElement): Int = when (element) {
        is JsonObject -> (if ("cache_control" in element) 1 else 0) + element.values.sumOf { breakpoints(it) }
        is JsonArray -> element.sumOf { breakpoints(it) }
        else -> 0
    }

    private fun JsonObject.messages() = this["messages"]!!.jsonArray.map { it.jsonObject }

    // ==================== The cached prefix ====================

    /** The three levels are three system blocks, in order, each its own breakpoint. */
    @Test
    fun theThreeLevels_areThreeCachedSystemBlocks() {
        val system = request(user("hello"))["system"]!!.jsonArray.map { it.jsonObject }

        assertEquals(listOf("L1 documentation", "L2 user data", "L3 app state"), system.map { it["text"]!!.jsonPrimitive.content })
        system.forEach { assertEquals("ephemeral", it["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content) }
    }

    /** The API refuses a fifth breakpoint: three levels and the end of the history make four. */
    @Test
    fun aRequestNeverCarriesMoreThanFourBreakpoints() {
        val body = request(user("q1"), ai("""{"preText":"a1"}"""), user("q2"), user("data"), ai("""{"preText":"a2"}"""), user("q3"))

        assertEquals(4, breakpoints(body))
    }

    /** The fourth breakpoint closes the history, on the last block of its last message. */
    @Test
    fun theLastHistoryMessage_carriesTheFourthBreakpointOnItsLastBlock() {
        val messages = request(user("q1"), ai("""{"preText":"a1"}"""), user("q2"), user("data")).messages()

        val lastHistory = messages[messages.size - 2]
        val blocks = lastHistory["content"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("q2", "data"), blocks.map { it["text"]!!.jsonPrimitive.content })
        assertFalse("cache_control" in blocks[0])
        assertTrue("cache_control" in blocks[1])
        // Earlier messages carry none: they are inside the cached prefix already
        assertEquals(0, breakpoints(JsonArray(messages.dropLast(2))))
    }

    /**
     * The dated message changes on every call, so it comes last and outside any breakpoint:
     * placed before one, it would invalidate the cache it precedes each time.
     */
    @Test
    fun theDatedMessage_comesLastWithNoBreakpoint() {
        val last = request(user("hello")).messages().last()

        assertEquals("user", last["role"]!!.jsonPrimitive.content)
        assertEquals("Current date and time: 2026-09-24T10:00:00+02:00", last["content"]!!.jsonPrimitive.content)
        assertEquals(0, breakpoints(last))
    }

    /** With no history yet, the levels are cached and the dated message stands alone. */
    @Test
    fun anEmptyHistory_leavesTheThreeLevelBreakpoints() {
        val body = request()

        assertEquals(3, breakpoints(body))
        assertEquals(1, body.messages().size)
    }

    // ==================== The history ====================

    /**
     * Consecutive user-side messages -- a user message and the data it asked for -- go out as
     * one user turn with a block each; the AI's own turn goes out as the JSON it wrote.
     */
    @Test
    fun consecutiveUserMessages_areFusedAndTheAITurnKeepsItsJson() {
        val messages = request(user("q1"), user("data"), ai("""{"preText":"a1"}"""), user("q2")).messages()

        assertEquals(listOf("user", "assistant", "user", "user"), messages.map { it["role"]!!.jsonPrimitive.content })
        assertEquals(listOf("q1", "data"), messages[0]["content"]!!.jsonArray.map { it.jsonObject["text"]!!.jsonPrimitive.content })
        assertEquals("""{"preText":"a1"}""", messages[1]["content"]!!.jsonPrimitive.content)
    }

    /**
     * An empty block is dropped: the API refuses an empty text block. An empty AI message is
     * dropped without splitting the user turn around it into two.
     */
    @Test
    fun blankMessages_areDropped() {
        val fused = fuseConsecutiveUserMessages(listOf(user("q1"), user("  "), ai(""), user("q2")))

        assertEquals(listOf(FusedMessage("user", listOf("q1", "q2"))), fused)
    }

    // ==================== Options ====================

    /** Effort goes inside output_config, and only when the endpoint declares one. */
    @Test
    fun effort_isSentInOutputConfigOnlyWhenSet() {
        assertEquals("high", request(user("q"), effort = "high")["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertNull(request(user("q"))["output_config"])
    }

    // ==================== Reading the answer ====================

    private fun answer(json: String) = Json.parseToJsonElement(json).toClaudeAIResponse()

    /** Text and the four usage counts land where the cost calculation reads them. */
    @Test
    fun aCompleteAnswer_readsTextAndUsage() {
        val response = answer(
            """{"content":[{"type":"text","text":"{\"preText\":\"ok\"}"}],"stop_reason":"end_turn",
               "usage":{"input_tokens":12,"cache_creation_input_tokens":3000,"cache_read_input_tokens":9000,"output_tokens":150}}"""
        )

        assertTrue(response.success)
        assertEquals("""{"preText":"ok"}""", response.content)
        assertEquals(12, response.inputTokens)
        assertEquals(3000, response.cacheWriteTokens)
        assertEquals(9000, response.cacheReadTokens)
        assertEquals(150, response.tokensUsed)
    }

    /** With thinking on, a thinking block comes first; the text is still the one read. */
    @Test
    fun aThinkingBlockBeforeTheText_isSkipped() {
        val response = answer(
            """{"content":[{"type":"thinking","thinking":""},{"type":"text","text":"the answer"}],
               "stop_reason":"end_turn","usage":{"input_tokens":1,"output_tokens":1}}"""
        )

        assertEquals("the answer", response.content)
    }

    /** An answer cut by max_tokens is unusable JSON: a configuration failure, not a success. */
    @Test
    fun anAnswerCutByMaxTokens_isAConfigFailure() {
        val response = answer("""{"content":[{"type":"text","text":"{\"preT"}],"stop_reason":"max_tokens"}""")

        assertFalse(response.success)
        assertEquals(AIFailure.CONFIG, response.failure)
    }

    /** An answer with no text block says so instead of passing on an empty string. */
    @Test
    fun anAnswerWithNoText_isAConfigFailure() {
        val response = answer("""{"content":[{"type":"thinking","thinking":""}],"stop_reason":"end_turn"}""")

        assertFalse(response.success)
        assertEquals(AIFailure.CONFIG, response.failure)
    }

    /** An error object in the body is a refusal from a provider that was reached. */
    @Test
    fun anErrorBody_isARefusal() {
        val response = answer("""{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""")

        assertFalse(response.success)
        assertEquals(AIFailure.REFUSED, response.failure)
        assertEquals("Overloaded", response.errorMessage)
    }
}
