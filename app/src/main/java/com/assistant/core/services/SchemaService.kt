package com.assistant.core.services

import android.content.Context
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.validation.Schema
import com.assistant.core.schemas.ZoneSchemaProvider
import com.assistant.core.ai.data.AIMessageSchemas
import com.assistant.core.ai.data.CommunicationModuleSchemas
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
     * Get a specific schema by ID
     */
    private suspend fun handleGetSchema(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val schemaId = params.optString("id", "")
        if (schemaId.isEmpty()) {
            return OperationResult.error("Schema ID is required")
        }

        LogManager.service("SchemaService.get() called with schemaId='$schemaId'")

        val schema = getSchemaById(schemaId)
        return if (schema != null) {
            OperationResult.success(mapOf(
                "schema_id" to schema.id,
                "content" to schema.content
            ))
        } else {
            OperationResult.error("Schema not found: $schemaId")
        }
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
     * Get schema by ID with automatic provider discovery
     */
    private fun getSchemaById(schemaId: String): Schema? {
        LogManager.service("Resolving schema for ID: $schemaId")

        // Try system schemas first (hardcoded providers)
        val systemSchema = getSystemSchema(schemaId)
        if (systemSchema != null) return systemSchema

        // Try tooltype schemas via discovery
        val tooltypeSchema = getTooltypeSchema(schemaId)
        if (tooltypeSchema != null) return tooltypeSchema

        LogManager.service("Schema not found: $schemaId", "WARN")
        return null
    }

    /**
     * Get system schemas (hardcoded providers)
     */
    private fun getSystemSchema(schemaId: String): Schema? {
        return when {
            schemaId.startsWith("zone_") -> {
                // Use ZoneSchemaProvider for zone schemas
                LogManager.service("System schema requested: $schemaId - using ZoneSchemaProvider")
                ZoneSchemaProvider.getSchema(schemaId, context)
            }
            schemaId.startsWith("app_config_") -> {
                // TODO: Get app config schemas from AppConfigService
                LogManager.service("App config schema requested: $schemaId - STUB implementation")
                null
            }
            schemaId == "ai_message_response" -> {
                // AI message response schema
                LogManager.service("AI schema requested: $schemaId - using AIMessageSchemas")
                AIMessageSchemas.getAIMessageResponseSchema(context)
            }
            schemaId.startsWith("communication_module_") -> {
                // Communication module schemas (IDs from ai_prompt_chunks.xml placeholders)
                LogManager.service("Communication module schema requested: $schemaId")
                // Map schemaId to module type (accept both underscore and no-underscore formats)
                val moduleType = when (schemaId) {
                    "communication_module_multiple_choice", "communication_module_multiplechoice" -> "MultipleChoice"
                    "communication_module_validation" -> "Validation"
                    else -> null
                }
                moduleType?.let { CommunicationModuleSchemas.getSchema(it, context) }
            }
            else -> null
        }
    }

    /**
     * Get tooltype schemas via ToolTypeManager discovery
     *
     * CRITICAL: Do NOT mask technical exceptions - they must propagate to AI
     * Only catch exceptions for individual tooltypes, not the outer loop
     */
    private fun getTooltypeSchema(schemaId: String): Schema? {
        LogManager.service("Searching for schema '$schemaId' across all tooltypes")

        // Outer try catches only ToolTypeManager.getAllToolTypes() failures (technical error)
        val allToolTypes = try {
            ToolTypeManager.getAllToolTypes()
        } catch (e: Exception) {
            // CRITICAL: Technical error accessing ToolTypeManager - let it propagate
            LogManager.service("Technical error accessing ToolTypeManager: ${e.message}", "ERROR", e)
            throw IllegalStateException("Failed to access ToolTypeManager: ${e.message}", e)
        }

        // Iterate through tooltypes - catch individual failures but continue searching
        for ((toolTypeName, toolType) in allToolTypes) {
            try {
                val schema = toolType.getSchema(schemaId, context)
                if (schema != null) {
                    LogManager.service("Found schema '$schemaId' in tooltype '$toolTypeName'")
                    return schema
                }
                // null means "schema not found in this tooltype" - continue searching
            } catch (e: Exception) {
                // Individual tooltype failure - log but continue (schema might exist in other tooltypes)
                LogManager.service("Failed to get schema from tooltype '$toolTypeName': ${e.message}", "WARN")
                // Continue to next tooltype
            }
        }

        // Schema not found in any tooltype (not an error, just doesn't exist)
        LogManager.service("Schema '$schemaId' not found in any tooltype")
        return null
    }

    /**
     * Get all available schema IDs in the system
     */
    private fun getAllSchemaIds(): List<String> {
        val schemaIds = mutableListOf<String>()

        // Add system schema IDs (hardcoded)
        schemaIds.addAll(getSystemSchemaIds())

        // Add tooltype schema IDs via discovery
        schemaIds.addAll(getTooltypeSchemaIds())

        LogManager.service("Found ${schemaIds.size} total schema IDs")
        return schemaIds.sorted()
    }

    /**
     * Get system schema IDs (hardcoded list)
     */
    private fun getSystemSchemaIds(): List<String> {
        return listOf(
            "zone_config",
            "app_config",
            // AI schemas
            "ai_message_response",
            "communication_module_multiplechoice",  // Generated by CommunicationModuleSchemas (no underscore)
            "communication_module_validation"
        )
    }

    /**
     * Get tooltype schema IDs via discovery
     */
    private fun getTooltypeSchemaIds(): List<String> {
        val schemaIds = mutableListOf<String>()

        try {
            val allToolTypes = ToolTypeManager.getAllToolTypes()
            LogManager.service("Found ${allToolTypes.size} tooltypes for schema discovery")

            for ((toolTypeName, toolType) in allToolTypes) {
                try {
                    // TODO: Add method to ToolTypeContract to list available schema IDs
                    // For now, assume standard pattern: {tooltype}_config, {tooltype}_data
                    schemaIds.add("${toolTypeName}_config")
                    schemaIds.add("${toolTypeName}_data")

                    LogManager.service("Added schema IDs for tooltype: $toolTypeName")
                } catch (e: Exception) {
                    LogManager.service("Failed to get schema IDs from tooltype '$toolTypeName': ${e.message}", "WARN")
                }
            }
        } catch (e: Exception) {
            LogManager.service("Failed to discover tooltype schemas: ${e.message}", "ERROR", e)
        }

        return schemaIds
    }

    /**
     * Verbalize schema operation
     * Schema queries are typically not exposed to AI actions (read-only)
     */
    override fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return s.shared("action_verbalize_unknown")
    }
}