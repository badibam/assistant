package com.assistant.core.ai.providers

import com.assistant.core.ai.data.MessageSender
import com.assistant.core.ai.data.PromptData
import com.assistant.core.ai.data.SessionMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the request sent to the OpenAI Responses API and the reading of its answer.
 *
 * OpenAI caches a stable prefix on its own, so what matters on the way out is the order:
 * the three levels first, the history, and the dated message last. On the way back, the
 * usage fields do not mean what Claude's mean -- input_tokens includes the cached ones --
 * and the conversion to the app's reading (uncached input apart) is what the cost screen
 * relies on.
 */
class OpenAIExtensionsTest {

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

    private fun request(vararg history: SessionMessage, effort: String? = null): JsonObject =
        PromptData(
            level1Content = "L1 documentation",
            level2Content = "L2 user data",
            level3Content = "L3 app state",
            sessionMessages = history.toList()
        ).toOpenAIJson("gpt-test", 1.0, 1000, effort, "Current date and time: 2026-09-24T10:00:00+02:00")

    private fun JsonObject.input() = this["input"]!!.jsonArray.map { it.jsonObject }

    // ==================== The request ====================

    /** The stable part comes first, the dated message last, so the prefix stays cacheable. */
    @Test
    fun theLevelsLead_andTheDatedMessageCloses() {
        val input = request(
            message(MessageSender.USER, text = "q1"),
            message(MessageSender.AI, aiJson = """{"preText":"a1"}"""),
            message(MessageSender.USER, text = "q2")
        ).input()

        assertEquals(
            listOf("system", "system", "system", "user", "assistant", "user", "user"),
            input.map { it["role"]!!.jsonPrimitive.content }
        )
        assertEquals("L1 documentation", input[0]["content"]!!.jsonPrimitive.content)
        assertEquals("""{"preText":"a1"}""", input[4]["content"]!!.jsonPrimitive.content)
        assertEquals("Current date and time: 2026-09-24T10:00:00+02:00", input.last()["content"]!!.jsonPrimitive.content)
    }

    /** An empty message is left out rather than sent as an empty turn. */
    /** The effort goes inside reasoning, "none" included, and only when one is chosen. */
    @Test
    fun effort_isSentInReasoningOnlyWhenSet() {
        assertEquals("none", request(message(MessageSender.USER, text = "q"), effort = "none")["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertNull(request(message(MessageSender.USER, text = "q"))["reasoning"])
    }

    @Test
    fun blankMessages_areLeftOut() {
        val input = request(message(MessageSender.USER, text = " "), message(MessageSender.USER, text = "q")).input()

        assertEquals(5, input.size)
    }

    // ==================== Reading the answer ====================

    private fun answer(json: String) = Json.parseToJsonElement(json).toOpenAIResponse()

    /**
     * input_tokens counts the cached tokens too; the app reads input as uncached only, so the
     * cached part is taken out of it and reported as a cache read.
     */
    @Test
    fun aCompleteAnswer_splitsCachedFromUncachedInput() {
        val response = answer(
            """{"status":"completed",
               "output":[{"type":"reasoning","summary":[]},
                         {"type":"message","content":[{"type":"output_text","text":"the answer"}]}],
               "usage":{"input_tokens":10000,"input_tokens_details":{"cached_tokens":8000},"output_tokens":90}}"""
        )

        assertTrue(response.success)
        assertEquals("the answer", response.content)
        assertEquals(2000, response.inputTokens)
        assertEquals(8000, response.cacheReadTokens)
        assertEquals(0, response.cacheWriteTokens)
        assertEquals(90, response.tokensUsed)
    }

    /** An answer that did not complete is cut, and its JSON unusable. */
    @Test
    fun anIncompleteAnswer_isAConfigFailure() {
        val response = answer("""{"status":"incomplete","output":[]}""")

        assertFalse(response.success)
        assertEquals(AIFailure.CONFIG, response.failure)
    }

    /**
     * A completed answer with no text says so. It used to pass as a success with an empty
     * content, which the parser downstream then failed on with nothing pointing here.
     */
    @Test
    fun aCompletedAnswerWithNoText_isAConfigFailure() {
        val response = answer("""{"status":"completed","output":[{"type":"reasoning","summary":[]}]}""")

        assertFalse(response.success)
        assertEquals(AIFailure.CONFIG, response.failure)
    }

    /** An error object in the body is a refusal from a provider that was reached. */
    @Test
    fun anErrorBody_isARefusal() {
        val response = answer("""{"error":{"message":"Rate limit reached"}}""")

        assertEquals(AIFailure.REFUSED, response.failure)
        assertEquals("Rate limit reached", response.errorMessage)
    }

    /** A null error, as the Responses API sends on success, is not an error. */
    @Test
    fun aNullErrorField_isNotAnError() {
        val response = answer(
            """{"error":null,"status":"completed",
               "output":[{"type":"message","content":[{"type":"output_text","text":"ok"}]}],
               "usage":{"input_tokens":1,"output_tokens":1}}"""
        )

        assertTrue(response.success)
    }

    /** No input or output count: the cost would read as free, so the call fails instead. */
    @Test
    fun anAnswerWithoutUsage_isAConfigFailure() {
        val response = answer(
            """{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"ok"}]}]}"""
        )

        assertFalse(response.success)
        assertEquals(AIFailure.CONFIG, response.failure)
    }
}
