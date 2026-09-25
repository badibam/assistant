package com.assistant.core.tools

import com.assistant.core.utils.JsonUtils
import android.content.Context
import com.assistant.core.utils.LogManager
import com.assistant.core.strings.Strings
import com.assistant.core.validation.ValidationException
import com.assistant.core.validation.FieldLimits
import com.assistant.core.fields.settings.FieldTypeSchemas
import com.assistant.core.fields.EntrySchemaGenerator
import com.assistant.core.fields.toFieldDefinitions

/**
 * Base JSON Schemas for all ToolTypes
 * Provides common fields to reduce token usage in AI prompts
 * Specific ToolTypes extend these base schemas with their own fields
 */
object BaseSchemas {
    
    /**
     * The data schema of a tool's entries, generated from what its tool type declares for
     * [config] and from the user's fields the config lists in extra_fields.
     *
     * The one schema an entry is held to: the service checks every write against it, the AI
     * reads it, and screens check against it before writing.
     *
     * @throws com.assistant.core.fields.ValidationException if a user field cannot be read
     */
    fun getEntrySchema(toolType: ToolTypeContract, config: org.json.JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        val extra = config.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()
        return EntrySchemaGenerator.generate(toolType.getEntryFields(config, context), extra, s::shared)
    }

    /**
     * The data schema of a tool type's entries as its getSchema hands it out: for the tool
     * instance [toolInstanceId] when given, read from its stored config, and for a new tool of
     * that type otherwise, from the type's default config.
     *
     * @throws IllegalStateException if the tool's config cannot be loaded or its fields read
     */
    fun getEntrySchema(toolType: ToolTypeContract, toolInstanceId: String?, context: Context): String {
        val config = if (toolInstanceId == null) org.json.JSONObject(toolType.getDefaultConfig())
                     else loadToolConfig(toolInstanceId, context)
        return getEntrySchemaOrThrow(toolType, config, toolInstanceId, context)
    }

    /**
     * The entry schema of the tool [toolInstanceId] as a Schema, for a caller checking an entry
     * before handing it to the service.
     *
     * @throws IllegalStateException if the tool's config cannot be loaded or its fields read
     */
    fun entrySchema(toolType: ToolTypeContract, toolInstanceId: String, context: Context): com.assistant.core.validation.Schema =
        com.assistant.core.validation.Schema(
            id = "entries:$toolInstanceId",
            displayName = toolType.getDisplayName(context),
            description = toolType.getDescription(context),
            category = com.assistant.core.validation.SchemaCategory.TOOL_DATA,
            content = getEntrySchema(toolType, toolInstanceId, context)
        )

    /**
     * [getEntrySchema] for a config the caller already holds, with its failure named after the
     * tool, so that a schema built from the wrong config never passes for the right one.
     *
     * @throws IllegalStateException if the config's fields cannot be read
     */
    fun getEntrySchemaOrThrow(toolType: ToolTypeContract, config: org.json.JSONObject, toolInstanceId: String?, context: Context): String =
        try {
            getEntrySchema(toolType, config, context)
        } catch (e: Exception) {
            throw IllegalStateException("Cannot read the fields of tool ${toolInstanceId ?: "(new)"}: ${e.message}", e)
        }

    /**
     * The stored config of the tool instance [toolInstanceId].
     *
     * @throws IllegalStateException if there is no such tool
     */
    fun loadToolConfig(toolInstanceId: String, context: Context): org.json.JSONObject {
        val tool = kotlinx.coroutines.runBlocking {
            com.assistant.core.database.AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(toolInstanceId)
        } ?: throw IllegalStateException("Cannot load the config of tool $toolInstanceId for its entry schema")
        return org.json.JSONObject(tool.config_json)
    }

    /**
     * Get human-readable name for common ToolType fields
     * Used for validation error messages
     * @param fieldName Technical field name (e.g., "management", "display_mode")
     * @param context Android context for string resource access
     * @return Translated field name or null if not a common field
     */
    fun getCommonFieldName(fieldName: String, context: Context): String? {
        val s = Strings.`for`(context = context)
        return when(fieldName) {
            "name" -> s.shared("tools_config_label_name")
            "description" -> s.shared("tools_config_label_description")
            "management" -> s.shared("tools_config_label_management")
            "display_mode" -> s.shared("tools_config_label_display_mode")
            "icon_name" -> s.shared("tools_config_label_icon")
            "validate_config" -> s.shared("tools_config_label_validate_config")
            "validate_data" -> s.shared("tools_config_label_validate_data")
            else -> null
        }
    }

}