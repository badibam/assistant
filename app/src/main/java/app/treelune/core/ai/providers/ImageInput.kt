package app.treelune.core.ai.providers

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.strings.StringsContext
import org.json.JSONObject

/**
 * Whether a provider's model reads images (docs/design/message-images.md). The provider's model
 * list says so for some (Anthropic: capabilities.image_input; OpenRouter: input_modalities): the
 * config screen stores what it said in [READS_IMAGES] when the config is saved. When it says
 * nothing, an input fact of provider-facts may, read at each use. A model nothing is known of is
 * refused like one that does not read images: some compatible servers drop a part they do not
 * understand without a word, and the model would answer on a photo it never saw.
 */
object ImageInput {

    /** The setting holding what the model list said; absent when it said nothing. */
    const val READS_IMAGES = "reads_images"

    /** The most images a request may carry: Claude refuses more than 100. */
    const val MAX_IMAGES = 100

    /** The setting, filled by the config screen alone: shown, never edited. */
    fun node(s: StringsContext): SettingNode = SettingNode.Field(
        FieldDefinition(READS_IMAGES, s.shared("ai_provider_reads_images"), s.shared("ai_provider_schema_reads_images"),
            FieldType.BOOLEAN, false, emptyMap())
    )

    /**
     * Whether [config]'s model reads images: what its list said, else what a fact says, else null.
     *
     * @param factsProvider The provider's name in the facts; null when no fact can name its models
     */
    fun readsImages(config: JSONObject, facts: ProviderFacts, factsProvider: String?): Boolean? {
        if (config.has(READS_IMAGES) && !config.isNull(READS_IMAGES)) return config.getBoolean(READS_IMAGES)
        val model = config.optString("model").ifEmpty { return null }
        return factsProvider?.let { facts.readsImages(it, model) }
    }

    /**
     * Whether the model a chat would be sent to — the active provider's — reads images; null when
     * nothing says, or no provider is active.
     */
    suspend fun activeModelReadsImages(context: Context): Boolean? {
        val coordinator = Coordinator(context)
        val active = coordinator.processUserAction("ai_provider_config.get_active")
        val providerId = active.data?.get("active_provider_id") as? String ?: return null
        val configResult = coordinator.processUserAction("ai_provider_config.get", mapOf("provider_id" to providerId))
        if (!configResult.isSuccess) return null
        @Suppress("UNCHECKED_CAST")
        val config = configResult.data?.get("config") as? Map<String, Any?> ?: return null
        val provider = AIProviderRegistry(context).getProvider(providerId) ?: return null
        return provider.readsImages(app.treelune.core.utils.JsonUtils.toJSONObject(config), context)
    }

    /**
     * Why a request carrying [imageCount] images cannot go to a model that [reads] them or not, or
     * null when it can: none refused, too many refused. [text] gives a shared string by its key.
     */
    fun refusal(imageCount: Int, reads: Boolean?, text: (String) -> String): String? = when {
        imageCount == 0 -> null
        reads == false -> text("ai_image_error_model_does_not_read")
        reads == null -> text("ai_image_error_model_unknown")
        imageCount > MAX_IMAGES -> text("ai_image_error_too_many").format(imageCount, MAX_IMAGES)
        else -> null
    }
}
