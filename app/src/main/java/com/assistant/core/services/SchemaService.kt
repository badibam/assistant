package com.assistant.core.services

import android.content.Context
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.tools.BaseSchemas
import com.assistant.core.tools.ToolConfigSettings
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.validation.SchemaCategory
import com.assistant.core.validation.Schema
import com.assistant.core.schemas.ZoneSettings
import com.assistant.core.fields.settings.FieldTypeSchemas
import com.assistant.core.ai.data.AIMessageSchemas
import com.assistant.core.ai.data.CommunicationModules
import com.assistant.core.utils.LogManager
import org.json.JSONObject

/**
 * Schema Service - Centralized access to all schemas in the system
 *
 * Provides unified access to schemas from:
 * - System schemas (zones, app_config, etc.) - hardcoded providers
 * - Tooltype schemas (tracking, journal, etc.) - via ToolTypeManager discovery
 *
 * Used by AI system to retrieve schema definitions for prompt generation.
 */
class SchemaService(private val context: Context) : ExecutableService {

    // Access ToolTypeManager object directly
    private val s = Strings.`for`(context = context)

    /**
     * Execute schema operation with cancellation support
     */
    override suspend fun execute(
        operation: String,
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        return try {
            when (operation) {
                "get" -> handleGetSchema(params, token)
                "list" -> handleListSchemas(params, token)
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        } catch (e: Exception) {
            LogManager.service("SchemaService operation failed: ${e.message}", "ERROR", e)
            OperationResult.error("Schema service error: ${e.message}")
        }
    }

    /**
     * The schema of what the params name: "tooltype", the config of a tool of that type;
     * "tool_instance_id", the entries of that tool; "id", any other schema by its name.
     *
     * A tool's schemas are generated from its type's declarations. Their name is computed from
     * what they describe ("tracking_config", "tracking_data") and handed back as "schema_id" so a
     * caller can tell two schemas apart; it is stored nowhere.
     */
    private suspend fun handleGetSchema(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val tooltype = params.optString("tooltype")
        val toolInstanceId = params.optString("tool_instance_id")
        val schemaId = params.optString("id")
        LogManager.service("SchemaService.get() called with tooltype='$tooltype', toolInstanceId='$toolInstanceId', id='$schemaId'")

        val schema = when {
            toolInstanceId.isNotEmpty() -> entrySchema(toolInstanceId)
            tooltype.isNotEmpty() -> configSchema(tooltype)
            schemaId.isNotEmpty() -> getSystemSchema(schemaId)
            else -> return OperationResult.error(s.shared("service_error_schema_target_missing"))
        } ?: return OperationResult.error(s.shared("service_error_schema_not_found").format(listOf(tooltype, toolInstanceId, schemaId).first { it.isNotEmpty() }))

        val resultData = mutableMapOf<String, Any>(
            "schema_id" to schema.id,
            "content" to schema.content
        )
        // Entries schemas are one per tool: the tool is part of what tells two of them apart
        if (toolInstanceId.isNotEmpty()) resultData["tool_instance_id"] = toolInstanceId

        return OperationResult.success(resultData)
    }

    /** The config schema of a tool of [tooltype], or null when there is no such type. */
    private fun configSchema(tooltype: String): Schema? {
        val toolType = ToolTypeManager.getToolType(tooltype) ?: return null
        return ToolConfigSettings.schema(toolType, "${tooltype}_config", context)
    }

    /**
     * The schema of the entries of the tool [toolInstanceId], from its current config.
     *
     * @throws IllegalStateException when the tool's config or fields cannot be read: an error
     *   for the caller, not a schema that does not exist
     */
    private suspend fun entrySchema(toolInstanceId: String): Schema? {
        val tool = com.assistant.core.database.AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(toolInstanceId)
            ?: return null
        val toolType = ToolTypeManager.getToolType(tool.tooltype) ?: return null
        return Schema(
            id = "${tool.tooltype}_data",
            displayName = toolType.getDisplayName(context),
            description = toolType.getDescription(context),
            category = SchemaCategory.TOOL_DATA,
            content = BaseSchemas.getEntrySchemaOrThrow(toolType, JSONObject(tool.config_json), toolInstanceId, context)
        )
    }

    /**
     * List all available schema IDs
     */
    private suspend fun handleListSchemas(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        LogManager.service("SchemaService.list() called")

        val schemaIds = getAllSchemaIds()
        return OperationResult.success(mapOf(
            "schema_ids" to schemaIds
        ))
    }

    // ========================================================================================
    // Schema Resolution Methods
    // ========================================================================================

    /**
     * Get system schemas (hardcoded providers)
     */
    private fun getSystemSchema(schemaId: String): Schema? {
        return when {
            schemaId == ZoneSettings.SCHEMA_ID -> ZoneSettings.schema(context)
            schemaId.startsWith("field_type_") -> FieldTypeSchemas.getSchema(schemaId, context)
            schemaId.startsWith("app_config_") -> com.assistant.core.config.AppSettings.CATEGORIES
                .find { schemaId == "app_config_$it" }?.let { com.assistant.core.config.AppSettings.schema(it, context) }
            schemaId == "ai_message_response" -> {
                // AI message response schema
                LogManager.service("AI schema requested: $schemaId - using AIMessageSchemas")
                AIMessageSchemas.getAIMessageResponseSchema(context)
            }
            schemaId == CommunicationModules.SCHEMA_ID -> CommunicationModules.schema(context)
            else -> null
        }
    }

    /**
     * Get all available schema IDs in the system
     */
    private fun getAllSchemaIds(): List<String> {
        val schemaIds = mutableListOf<String>()

        // Add system schema IDs (hardcoded)
        schemaIds.addAll(getSystemSchemaIds())

        // A tool type's config and entries, by their computed names
        ToolTypeManager.getAllToolTypes().keys.forEach { schemaIds.add("${it}_config"); schemaIds.add("${it}_data") }

        LogManager.service("Found ${schemaIds.size} total schema IDs")
        return schemaIds.sorted()
    }

    /**
     * Get system schema IDs (hardcoded list)
     */
    private fun getSystemSchemaIds(): List<String> {
        val schemaIds = mutableListOf(
            ZoneSettings.SCHEMA_ID,
            // AI schemas
            "ai_message_response",
            CommunicationModules.SCHEMA_ID
        )
        // One schema per category of the app's settings, by the name getSystemSchema serves
        schemaIds.addAll(com.assistant.core.config.AppSettings.CATEGORIES.map { "app_config_$it" })

        schemaIds.addAll(FieldTypeSchemas.getAllSchemaIds())

        return schemaIds
    }

    /**
     * Verbalize schema operation
     */
    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)

        return when (operation) {
            "get" -> {
                val target = listOf("tooltype", "tool_instance_id", "id").map { params.optString(it) }.firstOrNull { it.isNotEmpty() } ?: ""
                s.shared("action_verbalize_schema_get").format(target)
            }
            "list" -> s.shared("action_verbalize_schema_list")
            else -> s.shared("action_verbalize_unknown")
        }
    }
}