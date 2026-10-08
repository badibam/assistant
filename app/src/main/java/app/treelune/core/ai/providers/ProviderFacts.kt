package app.treelune.core.ai.providers

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * How a model turns its thinking off (a thinking_off fact): the thinking.type that does it, null
 * when the model cannot; the effort levels it is accepted at, null when every level is.
 */
data class ThinkingOff(val thinking: String?, val efforts: List<String>?)

/**
 * What the providers' facts say of a model's reasoning and of the images it reads, read from the app's copy of the
 * provider-facts project (assets/facts.json, checked by `./run provider-facts`). A provider's
 * model list says nothing of most of it: OpenAI's lists no effort level, Anthropic's declares
 * no way to turn thinking off. A model no fact covers has no such setting.
 *
 * @param facts The facts, as the copy holds them
 */
class ProviderFacts(private val facts: List<JsonObject>) {

    /** The effort levels of [model] at [provider] ("openai", "deepseek"…), null when no fact gives them. */
    fun effortLevels(provider: String, model: String): List<String>? =
        fact(provider, model, "effort_levels")?.let { strings(it["levels"]) }

    /** How [model] at [provider] turns its thinking off, null when no fact says. */
    fun thinkingOff(provider: String, model: String): ThinkingOff? =
        fact(provider, model, "thinking_off")?.let { fact ->
            ThinkingOff(
                thinking = fact["thinking"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content,
                efforts = fact["efforts"]?.takeIf { it !is JsonNull }?.let { strings(it) }
            )
        }

    /**
     * Whether [model] at [provider] reads images, null when no fact says. A model may be named by
     * several input facts, each on what it says (images, PDF): the copy holds at most one saying
     * anything of images.
     */
    fun readsImages(provider: String, model: String): Boolean? {
        val found = facts.filter { fact ->
            fact.string("provider") == provider && fact.string("kind") == "input" &&
                fact["images"].let { it != null && it !is JsonNull } &&
                fact["models"]!!.jsonArray.any { it.jsonPrimitive.content == model }
        }
        check(found.size <= 1) { "facts.json: ${found.size} input facts say whether $provider/$model reads images" }
        return found.firstOrNull()?.get("images")?.jsonPrimitive?.content?.toBooleanStrict()
    }

    /** The fact of [kind] naming [model] at [provider]: the copy holds at most one per model and kind. */
    private fun fact(provider: String, model: String, kind: String): JsonObject? {
        val found = facts.filter { fact ->
            fact.string("provider") == provider && fact.string("kind") == kind &&
                fact["models"]!!.jsonArray.any { it.jsonPrimitive.content == model }
        }
        check(found.size <= 1) { "facts.json: ${found.size} $kind facts name $provider/$model" }
        return found.firstOrNull()
    }

    private fun JsonObject.string(key: String): String = this[key]!!.jsonPrimitive.content

    private fun strings(element: JsonElement?): List<String> =
        element!!.jsonArray.map { it.jsonPrimitive.content }

    companion object {
        /** The copy, as the assets hold it. */
        private const val ASSET = "facts.json"

        @Volatile private var loaded: ProviderFacts? = null

        /** The facts of [json], the text of a facts.json. */
        fun parse(json: String): ProviderFacts =
            ProviderFacts((Json.parseToJsonElement(json) as JsonArray).map { it.jsonObject })

        /** The app's copy, read once. */
        fun of(context: Context): ProviderFacts = loaded ?: synchronized(this) {
            loaded ?: parse(context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }).also { loaded = it }
        }
    }
}
