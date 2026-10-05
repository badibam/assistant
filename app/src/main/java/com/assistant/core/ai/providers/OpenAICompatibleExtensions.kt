package com.assistant.core.ai.providers

import com.assistant.core.ai.data.PromptData
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Chat Completions (`/chat/completions`): the request and its reading, for any server that speaks
 * it — Ollama, llama.cpp, vLLM, LM Studio, OpenRouter. Pure functions, tested without network
 * (OpenAICompatibleExtensionsTest).
 */

/**
 * How far the server holds the answer to a form: nothing, any valid JSON, or the exact schema of
 * the AI's answer. A server that refuses the level asked for answers an error, shown as is: no
 * level is ever lowered by the app.
 */
internal enum class OutputForcing(val stored: String) {
    NONE("none"),
    JSON("json"),
    SCHEMA("schema");

    companion object {
        fun of(stored: String): OutputForcing =
            entries.find { it.stored == stored } ?: error("Unknown output forcing '$stored'")
    }
}

/**
 * Whether [address] is a server's base address the app can call: an https URL. Plain http is
 * refused: the prompt carries the user's data, and the point of a server of one's own is that
 * nobody else reads it.
 */
internal fun isHttpsAddress(address: String): Boolean =
    address.toHttpUrlOrNull()?.isHttps == true

/** [path] under the server's base [address] ("https://host/v1" + "models"), whatever its trailing slash. */
internal fun endpointOf(address: String, path: String): String = address.trimEnd('/') + "/" + path

/**
 * Transform PromptData to a Chat Completions request.
 *
 * L1, L2 and L3 go as one system message, first: some models' chat templates refuse a second
 * system message, or one after the conversation has begun. The conversation and the dated message
 * follow, as for OpenAI (conversationMessages).
 *
 * @param forcing How far the answer is held to a form
 * @param responseSchema The schema of the AI's answer as the model reads it (SchemaModelView),
 *   required when [forcing] is SCHEMA
 * @param datetimeText The dated closing message, built by the caller (buildDatetimeMessage)
 * @param imageData The JPEG of each image the messages carry, as base64 (AttachedImages)
 */
internal fun PromptData.toChatCompletionsJson(
    model: String,
    temperature: Double,
    maxTokens: Int,
    forcing: OutputForcing,
    responseSchema: JsonObject?,
    datetimeText: String,
    imageData: ImageData = { error("Image $it in a prompt built without image data") }
): JsonObject = buildJsonObject {
    put("model", model)
    put("temperature", temperature)
    put("max_tokens", maxTokens)
    when (forcing) {
        OutputForcing.NONE -> {}
        OutputForcing.JSON -> putJsonObject("response_format") { put("type", "json_object") }
        OutputForcing.SCHEMA -> putJsonObject("response_format") {
            put("type", "json_schema")
            putJsonObject("json_schema") {
                put("name", "ai_message_response")
                put("schema", responseSchema ?: error("Output forcing SCHEMA without a response schema"))
            }
        }
    }
    putJsonArray("messages") {
        addJsonObject {
            put("role", "system")
            put("content", listOf(level1Content, level2Content, level3Content).filter { it.isNotBlank() }.joinToString("\n\n"))
        }
        conversationMessages(datetimeText, ImageParts.CHAT_COMPLETIONS, imageData).forEach { add(it) }
    }
}

/**
 * Parse a Chat Completions response to AIResponse.
 *
 * {
 *   "choices": [{ "message": { "content": "..." }, "finish_reason": "stop" }],
 *   "usage": { "prompt_tokens": 36, "completion_tokens": 87, "prompt_tokens_details": { "cached_tokens": 0 } },
 *   "error": { "message": "..." }  // if error, some servers in a 200 body
 * }
 *
 * An answer cut by the length limit ("length") is refused: incomplete JSON is unusable.
 */
internal fun JsonElement.toChatCompletionsResponse(): AIResponse {
    val jsonObj = this.jsonObject

    fun failed(message: String, failure: AIFailure) = AIResponse(
        success = false,
        content = "",
        errorMessage = message,
        failure = failure,
        tokensUsed = 0,
        cacheWriteTokens = 0,
        cacheReadTokens = 0,
        inputTokens = 0
    )

    // An error object inside a 200 body: the call was answered, so it is a refusal
    (jsonObj["error"] as? JsonObject)?.let { error ->
        return failed(error["message"]?.jsonPrimitive?.contentOrNull ?: error.toString(), AIFailure.REFUSED)
    }

    val choice = (jsonObj["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
        ?: return failed("Provider response has no choices.", AIFailure.CONFIG)

    val finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
    if (finishReason == "length") {
        return failed("Response cut by the length limit. Increase max_output_tokens in provider config.", AIFailure.CONFIG)
    }

    val text = ((choice["message"] as? JsonObject)?.get("content") as? JsonPrimitive)?.contentOrNull
        ?: return failed("Provider response has no message content in its first choice.", AIFailure.CONFIG)

    // Usage: prompt and completion counts are required, since the cost is computed from them and a
    // missing one would show a paid call as free. The cached count is optional: none reported
    // means none was read from the cache.
    val usage = jsonObj["usage"] as? JsonObject
    val promptTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.intOrNull
    val completionTokens = usage?.get("completion_tokens")?.jsonPrimitive?.intOrNull
    if (promptTokens == null || completionTokens == null) {
        return failed("Provider response has no prompt_tokens or completion_tokens in its usage.", AIFailure.CONFIG)
    }
    val cachedTokens = (usage["prompt_tokens_details"] as? JsonObject)?.get("cached_tokens")?.jsonPrimitive?.intOrNull ?: 0

    // prompt_tokens includes the cached ones: inputTokens holds the uncached input only, as for Claude
    return AIResponse(
        success = true,
        content = text,
        errorMessage = null,
        tokensUsed = completionTokens,
        cacheWriteTokens = 0,
        cacheReadTokens = cachedTokens,
        inputTokens = promptTokens - cachedTokens
    )
}
