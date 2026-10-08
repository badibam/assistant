package app.treelune.core.ai.providers

import android.content.Context
import app.treelune.core.fields.settings.SettingValues
import app.treelune.core.fields.settings.SettingsSchemaGenerator
import app.treelune.core.strings.Strings
import app.treelune.core.validation.Schema
import app.treelune.core.validation.SchemaCategory
import org.json.JSONObject

/**
 * The config of an AI provider, declared with the fields (AIProvider.getConfigSettings): the
 * schema AIProviderConfigService checks every write against, and the single way code reads it.
 */
object AIProviderSettings {

    /** The schema a config of [provider] is held to. */
    fun schema(provider: AIProvider, context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = "ai_provider_${provider.getProviderId()}_config",
            displayName = provider.getDisplayName(),
            description = provider.getConfigHelp(context),
            category = SchemaCategory.AI_PROVIDER,
            content = SettingsSchemaGenerator.generate(provider.getConfigSettings(context), s::shared).toString()
        )
    }

    /** [config] of [provider], read through its declaration (SettingValues). */
    fun read(provider: AIProvider, config: JSONObject, context: Context): SettingValues =
        SettingValues(provider.getConfigSettings(context), config)
}
