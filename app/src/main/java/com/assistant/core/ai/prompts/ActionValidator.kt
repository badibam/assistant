package com.assistant.core.ai.prompts

import android.content.Context
import com.assistant.core.ai.data.ExecutableCommand
import com.assistant.core.strings.Strings
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.utils.LogManager
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.validation.ValidationResult
import org.json.JSONObject

/**
 * Validates actions before execution in CommandExecutor
 *
 * Per architectural decision: validation happens at CommandExecutor level for AI commands,
 * while UI commands are validated earlier via ValidationHelper.
 * This ensures all AI actions are validated without duplicating validation for UI.
 *
 * Validates:
 * - tools.create/update → tool configuration schemas
 * - tool_data.create/update/batch_* → tool data schemas
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
            // Tool instance configuration validation
            command.resource == "tools" && command.operation in listOf("create", "update") -> {
                validateToolConfig(command.params)
            }

            // Tool data validation
            command.resource == "tool_data" && command.operation in listOf(
                "create", "update", "batch_create", "batch_update"
            ) -> {
                validateToolData(command.params, command.operation)
            }

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
     * Validate tool configuration (tools.create/update)
     *
     * Expects params to contain:
     * - tool_type: String (tooltype name)
     * - config_json: String (JSON configuration to validate)
     */
    private fun validateToolConfig(params: Map<String, Any>): ValidationResult {
        try {
            val toolTypeName = params["tool_type"] as? String
            if (toolTypeName.isNullOrEmpty()) {
                LogManager.aiService("Missing tool_type in tools.create/update params", "ERROR")
                return ValidationResult.error(s.shared("error_missing_tool_type"))
            }

            val configJson = params["config_json"] as? String
            if (configJson.isNullOrEmpty()) {
                LogManager.aiService("Missing config_json in tools.create/update params", "ERROR")
                return ValidationResult.error(s.shared("error_missing_config"))
            }

            // Parse config JSON to Map
            val configData = JSONObject(configJson).let { json ->
                json.keys().asSequence().associateWith { key ->
                    json.get(key)
                }
            }

            // Get ToolType via ToolTypeManager
            val toolType = ToolTypeManager.getToolType(toolTypeName)
            if (toolType == null) {
                LogManager.aiService("ToolType not found: $toolTypeName", "ERROR")
                return ValidationResult.error(
                    s.shared("error_tooltype_not_found").format(toolTypeName)
                )
            }

            // Extract schema_id from config
            val schemaId = configData["schema_id"] as? String
            if (schemaId.isNullOrEmpty()) {
                LogManager.aiService("Missing schema_id in config for $toolTypeName", "ERROR")
                return ValidationResult.error(s.shared("error_missing_schema_id"))
            }

            // Get Schema via toolType.getSchema()
            val schema = toolType.getSchema(schemaId, context)
            if (schema == null) {
                LogManager.aiService("Schema not found: $schemaId for $toolTypeName", "ERROR")
                return ValidationResult.error("Schema not found: $schemaId")
            }

            // Validate via SchemaValidator
            val validationResult = SchemaValidator.validate(schema, configData, context)

            if (validationResult.isValid) {
                LogManager.aiService("Tool config validation successful for $toolTypeName", "DEBUG")
            } else {
                LogManager.aiService(
                    "Tool config validation failed for $toolTypeName: ${validationResult.errorMessage}",
                    "WARN"
                )
            }

            return validationResult

        } catch (e: Exception) {
            LogManager.aiService("Exception during tool config validation: ${e.message}", "ERROR", e)
            return ValidationResult.error("Validation error: ${e.message}")
        }
    }

    /**
     * Validate tool data (tool_data.create/update/batch_*)
     *
     * Expects params to contain:
     * - toolInstanceId: String (tool instance ID)
     * - tooltype: String (tooltype name)
     * - schema_id: String (schema ID for validation)
     * - data: JSONObject or Map (for single operations)
     * - items: List<Map> (for batch operations)
     */
    private fun validateToolData(params: Map<String, Any>, operation: String): ValidationResult {
        try {
            val toolTypeName = params["tooltype"] as? String
            if (toolTypeName.isNullOrEmpty()) {
                LogManager.aiService("Missing tooltype in tool_data.$operation params", "ERROR")
                return ValidationResult.error(s.shared("error_missing_tooltype"))
            }

            // Get ToolType via ToolTypeManager
            val toolType = ToolTypeManager.getToolType(toolTypeName)
            if (toolType == null) {
                LogManager.aiService("ToolType not found: $toolTypeName", "ERROR")
                return ValidationResult.error(
                    s.shared("error_tooltype_not_found").format(toolTypeName)
                )
            }

            // Handle batch vs single operations differently
            return when (operation) {
                "batch_create", "batch_update" -> {
                    // For batch operations, schema_id is in each entry
                    // Note: ToolDataService expects "entries" parameter
                    val entries = params["entries"] as? List<*>
                    if (entries.isNullOrEmpty()) {
                        LogManager.aiService("Missing or empty entries in batch operation", "ERROR")
                        return ValidationResult.error("Missing entries for batch operation")
                    }

                    // Get schema_id from first entry (all entries should have same tooltype/schema)
                    val firstItem = entries[0]
                    val firstItemMap = when (firstItem) {
                        is Map<*, *> -> firstItem as? Map<String, Any> ?: emptyMap()
                        is JSONObject -> firstItem.keys().asSequence().associateWith { firstItem.get(it) }
                        else -> {
                            LogManager.aiService("Invalid first item type in batch", "ERROR")
                            return ValidationResult.error("Invalid item format")
                        }
                    }

                    val schemaId = firstItemMap["schema_id"] as? String
                    if (schemaId.isNullOrEmpty()) {
                        LogManager.aiService("Missing schema_id in batch items", "ERROR")
                        return ValidationResult.error(s.shared("error_missing_schema_id"))
                    }

                    // Get Schema via toolType.getSchema()
                    val schema = toolType.getSchema(schemaId, context)
                    if (schema == null) {
                        LogManager.aiService("Schema not found: $schemaId for $toolTypeName", "ERROR")
                        return ValidationResult.error("Schema not found: $schemaId")
                    }

                    validateBatchToolData(params, schema, schemaId, toolTypeName, operation)
                }
                "create", "update" -> {
                    // For single operations, schema_id is at params level
                    val schemaId = params["schema_id"] as? String
                    if (schemaId.isNullOrEmpty()) {
                        LogManager.aiService("Missing schema_id in tool_data.$operation params", "ERROR")
                        return ValidationResult.error(s.shared("error_missing_schema_id"))
                    }

                    // Get Schema via toolType.getSchema()
                    val schema = toolType.getSchema(schemaId, context)
                    if (schema == null) {
                        LogManager.aiService("Schema not found: $schemaId for $toolTypeName", "ERROR")
                        return ValidationResult.error("Schema not found: $schemaId")
                    }

                    validateSingleToolData(params, schema, schemaId, toolTypeName, operation)
                }
                else -> ValidationResult.success() // Shouldn't reach here
            }

        } catch (e: Exception) {
            LogManager.aiService("Exception during tool data validation: ${e.message}", "ERROR", e)
            return ValidationResult.error("Validation error: ${e.message}")
        }
    }

    /**
     * Validate single tool data entry
     * @param operation The operation being performed (create, update, etc.)
     */
    private fun validateSingleToolData(
        params: Map<String, Any>,
        schema: com.assistant.core.validation.Schema,
        schemaId: String,
        toolTypeName: String,
        operation: String
    ): ValidationResult {
        val dataObj = params["data"]
        val dataMap = when (dataObj) {
            is JSONObject -> dataObj.keys().asSequence().associateWith { dataObj.get(it) }
            is Map<*, *> -> dataObj as? Map<String, Any> ?: emptyMap()
            else -> {
                LogManager.aiService("Invalid data type in tool_data params: ${dataObj?.javaClass?.simpleName}", "ERROR")
                return ValidationResult.error("Invalid data format")
            }
        }

        // Build complete validation structure (same as UI does in TrackingInputManager:54-61)
        // Schema expects: tool_instance_id, tooltype, name, timestamp, schema_id, data (nested object)
        val fullDataMap = mutableMapOf<String, Any>()

        // Add base fields from params
        params["toolInstanceId"]?.let { fullDataMap["tool_instance_id"] = it }
        params["tooltype"]?.let { fullDataMap["tooltype"] = it }
        params["name"]?.let { fullDataMap["name"] = it }
        params["timestamp"]?.let { fullDataMap["timestamp"] = it }

        // Add schema_id
        fullDataMap["schema_id"] = schemaId

        // Keep data as nested object (required by schema)
        fullDataMap["data"] = dataMap

        // Use partial validation for updates (only validate fields that are present)
        val partialValidation = operation == "update"
        val validationResult = SchemaValidator.validate(schema, fullDataMap, context, partialValidation)

        if (validationResult.isValid) {
            LogManager.aiService("Tool data validation successful for $toolTypeName", "DEBUG")
        } else {
            LogManager.aiService(
                "Tool data validation failed for $toolTypeName: ${validationResult.errorMessage}",
                "WARN"
            )
        }

        return validationResult
    }

    /**
     * Validate batch tool data entries
     * @param operation The operation being performed (batch_create, batch_update, etc.)
     */
    private fun validateBatchToolData(
        params: Map<String, Any>,
        schema: com.assistant.core.validation.Schema,
        schemaId: String,
        toolTypeName: String,
        operation: String
    ): ValidationResult {
        val entries = params["entries"] as? List<*>
        if (entries.isNullOrEmpty()) {
            LogManager.aiService("Missing or empty entries in batch operation", "ERROR")
            return ValidationResult.error("Missing entries for batch operation")
        }

        // Get base fields from params (shared across all entries)
        val toolInstanceId = params["toolInstanceId"]
        val tooltype = params["tooltype"]

        // Validate each entry in batch
        entries.forEachIndexed { index, item ->
            val itemMap = when (item) {
                is Map<*, *> -> item as? Map<String, Any> ?: emptyMap()
                is JSONObject -> item.keys().asSequence().associateWith { item.get(it) }
                else -> {
                    LogManager.aiService("Invalid item type at index $index: ${item?.javaClass?.simpleName}", "ERROR")
                    return ValidationResult.error("Invalid item format at index $index")
                }
            }

            // Extract data field from entry
            val dataObj = itemMap["data"]
            val dataMap = when (dataObj) {
                is JSONObject -> dataObj.keys().asSequence().associateWith { dataObj.get(it) }
                is Map<*, *> -> dataObj as? Map<String, Any> ?: emptyMap()
                else -> emptyMap()
            }

            // Build complete validation structure (same as single operation)
            // Schema expects: tool_instance_id, tooltype, name, timestamp, schema_id, data (nested object)
            val fullDataMap = mutableMapOf<String, Any>()

            // Add base fields (shared from params level)
            toolInstanceId?.let { fullDataMap["tool_instance_id"] = it }
            tooltype?.let { fullDataMap["tooltype"] = it }

            // Add entry-specific fields
            itemMap["name"]?.let { fullDataMap["name"] = it }
            itemMap["timestamp"]?.let { fullDataMap["timestamp"] = it }

            // Add schema_id
            fullDataMap["schema_id"] = schemaId

            // Keep data as nested object (required by schema)
            fullDataMap["data"] = dataMap

            // Use partial validation for batch_update (only validate fields that are present)
            val partialValidation = operation == "batch_update"
            val validationResult = SchemaValidator.validate(schema, fullDataMap, context, partialValidation)

            if (!validationResult.isValid) {
                LogManager.aiService(
                    "Batch validation failed at index $index for $toolTypeName: ${validationResult.errorMessage}",
                    "WARN"
                )
                return ValidationResult.error("Item $index: ${validationResult.errorMessage}")
            }
        }

        LogManager.aiService("Batch tool data validation successful for $toolTypeName (${entries.size} entries)", "DEBUG")
        return ValidationResult.success()
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
    private fun validateZoneConfig(params: Map<String, Any>): ValidationResult {
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
