package app.treelune.core.ai.providers

import app.treelune.core.ai.data.MessageSender
import app.treelune.core.ai.data.PromptData
import app.treelune.core.ai.data.PromptPart
import app.treelune.core.ai.data.SessionMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers a message with images on its way to each provider (docs/design/message-images.md): the
 * images go where the user put them among the text, as base64 in Claude's image block, as a
 * `data:` URL in OpenAI's input_image and in Chat Completions' image_url.
 */
class ImageRequestsTest {

    // "compare [photo a] with [photo b]", as PromptManager prepares it
    private val withImages = SessionMessage(
        id = "m", timestamp = 0L, sender = MessageSender.USER, richContent = null, textContent = null,
        aiMessage = null, aiMessageJson = null, systemMessage = null,
        promptParts = listOf(PromptPart.Text("compare"), PromptPart.Image("a"), PromptPart.Text("with"), PromptPart.Image("b"))
    )
    private val prompt = PromptData("L1", "L2", "L3", listOf(withImages))
    private val imageData: ImageData = { "BASE64-$it" }
    private val datetime = "Current date and time: 2026-10-05T10:00:00+02:00"

    private fun JsonObject.type() = this["type"]!!.jsonPrimitive.content

    @Test
    fun claude_sendsEachImageAsABase64Block_inItsPlace() {
        val content = prompt.toClaudeJson("claude-test", 1000, null, null, datetime, imageData)["messages"]!!
            .jsonArray.first().jsonObject["content"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("text", "image", "text", "image"), content.map { it.type() })
        val source = content[1]["source"]!!.jsonObject
        assertEquals("base64", source.type())
        assertEquals("image/jpeg", source["media_type"]!!.jsonPrimitive.content)
        assertEquals("BASE64-a", source["data"]!!.jsonPrimitive.content)
        // The last block of the last message carries the cache breakpoint, an image as well as a text
        assertEquals(true, "cache_control" in content.last())
    }

    @Test
    fun openAI_sendsEachImageAsAnInputImage_inItsPlace() {
        val content = prompt.toOpenAIJson("gpt-test", 1.0, 1000, null, datetime, imageData)["input"]!!
            .jsonArray.map { it.jsonObject }.first { it["role"]!!.jsonPrimitive.content == "user" }["content"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("input_text", "input_image", "input_text", "input_image"), content.map { it.type() })
        assertEquals("data:image/jpeg;base64,BASE64-b", content[3]["image_url"]!!.jsonPrimitive.content)
    }

    @Test
    fun chatCompletions_sendsEachImageAsAnImageUrl_inItsPlace() {
        val content = prompt.toChatCompletionsJson("model-test", 1.0, 1000, OutputForcing.NONE, null, datetime, imageData)["messages"]!!
            .jsonArray.map { it.jsonObject }.first { it["role"]!!.jsonPrimitive.content == "user" }["content"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("text", "image_url", "text", "image_url"), content.map { it.type() })
        assertEquals("data:image/jpeg;base64,BASE64-a", content[1]["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content)
    }
}
