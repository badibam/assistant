package com.assistant.core.ai.providers

import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.TextLength
import com.assistant.core.fields.settings.SettingNode
import com.assistant.core.strings.StringsContext
import org.json.JSONObject

/**
 * The reasoning settings of a provider's config, the same for every provider that has them: the
 * effort, and thinking turned off. What a model offers comes from its provider's list or from the
 * facts (ProviderFacts); the config screen offers only that, and a model it does not know has none.
 *
 * Thinking off never means the model's default effort: an effort is then chosen, among the levels
 * the thinking_off fact accepts it at, and sent with it. With thinking on, no effort chosen is the
 * model's default, unless that default is unknown (effortRequired).
 */
internal object ReasoningSettings {

    const val EFFORT = "effort"
    const val THINKING_OFF = "thinking_off"

    /**
     * The settings: the effort, and thinking off for a provider whose facts may say how to turn
     * it off ([thinkingOff]; OpenAI's "none" is an effort level instead). Both are chosen by the
     * config screen among what the model offers, so the effort is a text: its levels depend on
     * the model, and the checks are [error]'s.
     */
    fun nodes(s: StringsContext, thinkingOff: Boolean): List<SettingNode> = listOfNotNull(
        if (!thinkingOff) null
        else SettingNode.Field(FieldDefinition(THINKING_OFF, s.shared("ai_provider_thinking_off"), s.shared("ai_provider_schema_thinking_off"),
            FieldType.BOOLEAN, false, emptyMap()), default = false),
        SettingNode.Field(FieldDefinition(EFFORT, s.shared("ai_provider_effort"), s.shared("ai_provider_schema_effort"),
            FieldType.TEXT, false, mapOf("length" to TextLength.SHORT.name)))
    )

    /**
     * What [model] lets its config set, or null when nothing.
     *
     * @param factsProvider The provider's name in the facts ("anthropic", "openai", "deepseek")
     * @param listedEfforts The effort levels the provider's list declares for it; null when its
     *   list says nothing of them, the facts saying it then
     * @param effortRequired The model's default effort is unknown
     */
    fun of(facts: ProviderFacts, factsProvider: String, model: String, listedEfforts: List<String>?, effortRequired: Boolean): Reasoning? {
        val efforts = listedEfforts ?: facts.effortLevels(factsProvider, model) ?: emptyList()
        val thinkingOff = facts.thinkingOff(factsProvider, model)?.takeIf { it.thinking != null }
        return if (efforts.isEmpty() && thinkingOff == null) null else Reasoning(efforts, effortRequired, thinkingOff)
    }

    /**
     * Why [config]'s reasoning settings cannot be stored, or null when they can. Thinking off
     * needs a fact saying how, and an effort it accepts. Levels the facts give are checked; levels
     * a provider's list gives (effortsFromFacts false) are checked by the screen offering only them.
     */
    fun error(config: JSONObject, facts: ProviderFacts, factsProvider: String, effortsFromFacts: Boolean, effortRequired: Boolean, s: StringsContext): String? {
        val model = config.optString("model")
        val effort = config.optString(EFFORT).ifBlank { null }
        if (config.optBoolean(THINKING_OFF)) {
            val off = facts.thinkingOff(factsProvider, model)
            if (off?.thinking == null) return s.shared("ai_provider_error_thinking_off").format(model)
            if (effort == null) return s.shared("ai_provider_error_effort_with_thinking_off")
            if (off.efforts != null && effort !in off.efforts) return s.shared("ai_provider_error_effort_not_allowed").format(off.efforts.joinToString(", "))
        }
        if (effortsFromFacts) {
            val levels = facts.effortLevels(factsProvider, model)
            if (effort != null && (levels == null || effort !in levels)) return s.shared("ai_provider_error_effort_unknown").format(model, effort)
            if (effort == null && effortRequired && levels != null) return s.shared("ai_provider_error_effort_required")
        }
        return null
    }

    /** The thinking.type that turns [model]'s thinking off, null when its config keeps it on. */
    fun thinkingType(thinkingOff: Boolean, facts: ProviderFacts, factsProvider: String, model: String): String? =
        if (!thinkingOff) null
        else facts.thinkingOff(factsProvider, model)?.thinking
            ?: error("Config turns thinking off for $factsProvider/$model, which no fact says how to")
}
