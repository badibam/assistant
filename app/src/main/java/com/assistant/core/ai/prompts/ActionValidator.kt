package com.assistant.core.ai.prompts

import android.content.Context
import com.assistant.core.ai.data.ExecutableCommand
import com.assistant.core.strings.Strings
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.utils.LogManager
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.validation.ValidationResult
import com.assistant.core.utils.JsonUtils
import org.json.JSONObject

/**
 * Validates actions before execution in CommandExecutor
 *
 * A tool's config and its entries are not checked here: their services check every write,
 * whoever makes it (ToolInstanceService, ToolDataService).
 *
 * Validates:
 * - zones.create/update → zone configuration schemas
 */
class ActionValidator(private val context: Context) {

    private val s = Strings.`for`(context = context)

    /**
     * Validate an ExecutableCommand before execution
     *
     * @param command The command to validate
     * @return ValidationResult with isValid and errorMessage
     */
    fun validate(command: ExecutableCommand): ValidationResult {
        LogManager.aiService("ActionValidator checking: ${command.resource}.${command.operation}", "DEBUG")

        return when {
            // Zone configuration validation
            command.resource == "zones" && command.operation in listOf("create", "update") -> {
                validateZoneConfig(command.params)
            }

            // No validation required for other operations
            else -> {
                LogManager.aiService("No validation required for ${command.resource}.${command.operation}", "DEBUG")
                ValidationResult.success()
            }
        }
    }

    /**
     * Validate zone configuration (zones.create/update)
     *
     * Uses SchemaValidator with zone_config schema from ZoneSchemaProvider
     *
     * Expects params to contain:
     * - zone_id: String (routing parameter, filtered before validation)
     * - name: String (zone name, max 60 chars)
     * - icon_name: String (optional, icon identifier)
     * - description: String (optional, max 250 chars)
     * - color: String (optional)
     */
    private fun validateZoneConfig(params: Map<String, Any?>): ValidationResult {
        try {
            // Get ZoneSchemaProvider (registered in SchemaService)
            val schemaProvider = com.assistant.core.schemas.ZoneSchemaProvider
            val schema = schemaProvider.getSchema("zone_config", context)

            if (schema == null) {
                LogManager.aiService("Zone config schema not found", "ERROR")
                return ValidationResult.error("Zone config schema not found")
            }

            // Filter routing parameters before validation (zone_id is not part of config schema)
            val configParams = params.filterKeys { it != "zone_id" }

            // Validate via SchemaValidator
            val validationResult = SchemaValidator.validate(schema, configParams, context)

            if (validationResult.isValid) {
                LogManager.aiService("Zone config validation successful", "DEBUG")
            } else {
                LogManager.aiService(
                    "Zone config validation failed: ${validationResult.errorMessage}",
                    "WARN"
                )
            }

            return validationResult

        } catch (e: Exception) {
            LogManager.aiService("Exception during zone config validation: ${e.message}", "ERROR", e)
            return ValidationResult.error("Validation error: ${e.message}")
        }
    }
}
