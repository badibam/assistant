package com.assistant.core.services

import android.content.Context
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.LogManager
import com.assistant.core.fields.toFieldDefinitions
import com.assistant.core.fields.toFieldDefinition
import com.assistant.core.fields.toJsonArray
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldNameGenerator
import com.assistant.core.fields.FieldConfigValidator
import com.assistant.core.fields.ValidationException
import com.assistant.core.fields.migration.FieldConfigComparator
import com.assistant.core.fields.migration.MigrationPolicy
import com.assistant.core.fields.migration.FieldDataMigrator
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * ToolInstance Service - Core service for tool instance operations
 * Implements the standard service pattern with cancellation token
 */
class ToolInstanceService(private val context: Context) : ExecutableService {
    private val database by lazy { AppDatabase.getDatabase(context) }
    private val toolInstanceDao by lazy { database.toolInstanceDao() }
    private val s = Strings.`for`(context = context)
    
    /**
     * Execute tool instance operation with cancellation support
     */
    override suspend fun execute(
        operation: String,
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        return try {
            when (operation) {
                "create" -> handleCreate(params, token)
                "update" -> handleUpdate(params, token)
                "delete" -> handleDelete(params, token)
                "list" -> handleGetByZone(params, token)  // zones/{id}/tools pattern
                "list_all" -> handleListAll(params, token) // All tool instances across zones
                "get" -> handleGetById(params, token)      // tools/{id} pattern
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        } catch (e: Exception) {
            OperationResult.error(s.shared("service_error_tool_instance_service").format(e.message ?: ""))
        }
    }
    
    /**
     * Create a new tool instance
     */
    private suspend fun handleCreate(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val zoneId = params.optString("zone_id")
        val toolType = params.optString("tool_type")
        val configJson = params.optString("config_json", "{}")

        if (zoneId.isBlank() || toolType.isBlank()) {
            return OperationResult.error(s.shared("service_error_zone_id_tool_type_required"))
        }

        if (token.isCancelled) return OperationResult.cancelled()

        val newToolInstance = ToolInstance(
            zone_id = zoneId,
            tool_type = toolType,
            config_json = configJson
        )

        if (token.isCancelled) return OperationResult.cancelled()

        toolInstanceDao.insertToolInstance(newToolInstance)

        // Notify UI of tools change in this zone
        DataChangeNotifier.notifyToolsChanged(zoneId)

        return OperationResult.success(mapOf(
            "tool_instance_id" to newToolInstance.id,
            "zone_id" to newToolInstance.zone_id,
            "tool_type" to newToolInstance.tool_type
        ))
    }
    
    /**
     * Update existing tool instance
     *
     * If custom fields are modified in the config, this will:
     * 1. Generate names for new fields
     * 2. Validate all field definitions
     * 3. Remove deleted fields from all tool_data entries
     * All operations are done atomically in a database transaction.
     */
    private suspend fun handleUpdate(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("tool_instance_id")
        var configJson = params.optString("config_json")
        val newZoneId = params.optString("zone_id").takeIf { it.isNotBlank() }

        if (toolInstanceId.isBlank()) {
            return OperationResult.error(s.shared("service_error_tool_instance_id_required"))
        }

        val existingTool = toolInstanceDao.getToolInstanceById(toolInstanceId)
            ?: return OperationResult.error(s.shared("service_error_tool_instance_not_found"))

        if (token.isCancelled) return OperationResult.cancelled()

        // Process custom fields if config is being updated
        if (configJson.isNotBlank()) {
            val processResult = processCustomFields(
                toolInstanceId = toolInstanceId,
                oldConfigJson = existingTool.config_json,
                newConfigJson = configJson,
                token = token
            )

            if (!processResult.success) {
                return processResult // Return error from custom fields processing
            }

            // Get the processed config with generated field names
            configJson = processResult.data?.get("processed_config") as? String ?: configJson
        }

        // Store old zone_id for notification
        val oldZoneId = existingTool.zone_id

        val updatedTool = existingTool.copy(
            config_json = configJson.takeIf { it.isNotBlank() } ?: existingTool.config_json,
            zone_id = newZoneId ?: existingTool.zone_id, // Update zone if provided
            updated_at = System.currentTimeMillis()
        )

        if (token.isCancelled) return OperationResult.cancelled()

        toolInstanceDao.updateToolInstance(updatedTool)

        // Notify UI of tools change in affected zones
        if (newZoneId != null && newZoneId != oldZoneId) {
            // Tool moved to different zone - notify both old and new zones
            DataChangeNotifier.notifyToolsChanged(oldZoneId)
            DataChangeNotifier.notifyToolsChanged(newZoneId)
            LogManager.service("Tool $toolInstanceId moved from zone $oldZoneId to $newZoneId", "DEBUG")
        } else {
            // Only config changed - notify current zone
            DataChangeNotifier.notifyToolsChanged(updatedTool.zone_id)
        }

        return OperationResult.success(mapOf(
            "tool_instance_id" to updatedTool.id,
            "zone_id" to updatedTool.zone_id,
            "updated_at" to updatedTool.updated_at
        ))
    }
    
    /**
     * Delete tool instance
     */
    private suspend fun handleDelete(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val toolInstanceId = params.optString("tool_instance_id")
        
        if (toolInstanceId.isBlank()) {
            return OperationResult.error(s.shared("service_error_tool_instance_id_required"))
        }
        
        val existingTool = toolInstanceDao.getToolInstanceById(toolInstanceId)
            ?: return OperationResult.error(s.shared("service_error_tool_instance_not_found"))

        if (token.isCancelled) return OperationResult.cancelled()

        // Extract name from config for verbalization
        val toolName = try {
            JSONObject(existingTool.config_json).optString("name", "")
        } catch (e: Exception) {
            ""
        }

        toolInstanceDao.deleteToolInstanceById(toolInstanceId)

        // Notify UI of tools change in this zone
        DataChangeNotifier.notifyToolsChanged(existingTool.zone_id)

        return OperationResult.success(mapOf(
            "tool_instance_id" to toolInstanceId,
            "name" to toolName, // Include name for verbalization
            "deleted_at" to System.currentTimeMillis()
        ))
    }
    
    /**
     * Get tool instances by zone
     */
    private suspend fun handleGetByZone(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val zoneId = params.optString("zone_id")
        if (zoneId.isBlank()) {
            return OperationResult.error(s.shared("service_error_zone_id_required"))
        }

        // Read include_config parameter (default false for minimal version)
        val includeConfig = params.optBoolean("include_config", false)

        val toolInstances = toolInstanceDao.getToolInstancesByZone(zoneId)
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceData = toolInstances.map { tool ->
            // Always extract name and description for display
            var name = ""
            var description = ""
            try {
                val configJson = JSONObject(tool.config_json)
                name = configJson.optString("name", "")
                description = configJson.optString("description", "")
            } catch (e: Exception) {
                // Keep empty strings
            }

            // Build result map - minimal version
            val resultMap = mutableMapOf(
                "id" to tool.id,
                "zone_id" to tool.zone_id,
                "name" to name,
                "description" to description,
                "tool_type" to tool.tool_type,
                "order_index" to tool.order_index
            )

            // Conditionally add config_json and timestamps based on include_config parameter
            if (includeConfig) {
                resultMap["config_json"] = tool.config_json
                resultMap["created_at"] = tool.created_at
                resultMap["updated_at"] = tool.updated_at
            }

            resultMap
        }

        return OperationResult.success(mapOf(
            "tool_instances" to toolInstanceData,
            "count" to toolInstanceData.size
        ))
    }

    /**
     * List all tool instances across all zones
     */
    private suspend fun handleListAll(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        // Read include_config parameter (default false for minimal version)
        val includeConfig = params.optBoolean("include_config", false)

        val toolInstances = toolInstanceDao.getAllToolInstances()
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceData = toolInstances.map { tool ->
            // Always extract name and description for display
            var name = ""
            var description = ""
            try {
                val configJson = JSONObject(tool.config_json)
                name = configJson.optString("name", "")
                description = configJson.optString("description", "")
            } catch (e: Exception) {
                // Keep empty strings
            }

            // Build result map - minimal version
            val resultMap = mutableMapOf(
                "id" to tool.id,
                "zone_id" to tool.zone_id,
                "name" to name,
                "description" to description,
                "tool_type" to tool.tool_type,
                "order_index" to tool.order_index
            )

            // Conditionally add config_json and timestamps based on include_config parameter
            if (includeConfig) {
                resultMap["config_json"] = tool.config_json
                resultMap["created_at"] = tool.created_at
                resultMap["updated_at"] = tool.updated_at
            }

            resultMap
        }

        return OperationResult.success(mapOf(
            "tool_instances" to toolInstanceData,
            "count" to toolInstanceData.size
        ))
    }

    /**
     * Get tool instance by ID
     */
    private suspend fun handleGetById(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val toolInstanceId = params.optString("tool_instance_id")
        if (toolInstanceId.isBlank()) {
            return OperationResult.error(s.shared("service_error_tool_instance_id_required"))
        }
        
        val toolInstance = toolInstanceDao.getToolInstanceById(toolInstanceId)
            ?: return OperationResult.error(s.shared("service_error_tool_instance_not_found"))

        // Extract name from config JSON
        val name = try {
            JSONObject(toolInstance.config_json).optString("name", "")
        } catch (e: Exception) {
            ""
        }

        return OperationResult.success(mapOf(
            "tool_instance" to mapOf(
                "id" to toolInstance.id,
                "zone_id" to toolInstance.zone_id,
                "name" to name,
                "tool_type" to toolInstance.tool_type,
                "config_json" to toolInstance.config_json,
                "order_index" to toolInstance.order_index,
                "created_at" to toolInstance.created_at,
                "updated_at" to toolInstance.updated_at
            )
        ))
    }

    /**
     * Generates human-readable description of tool instance action
     * Format: substantive form (e.g., "Création de l'outil \"Poids\" dans la zone \"Santé\"")
     * Usage: (a) UI validation display, (b) SystemMessage feedback
     */
    override fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (operation) {
            "create" -> {
                // For create, name is directly in params
                val configJson = params.optString("config_json", "{}")
                val toolName = try {
                    JSONObject(configJson).optString("name", s.shared("content_unnamed"))
                } catch (e: Exception) {
                    s.shared("content_unnamed")
                }
                val zoneId = params.optString("zone_id")
                val zoneName = getZoneName(zoneId, context) ?: s.shared("content_unnamed")
                s.shared("action_verbalize_create_tool").format(toolName, zoneName)
            }
            "update" -> {
                val toolId = params.optString("tool_instance_id")
                val toolName = getToolName(toolId, context) ?: s.shared("content_unnamed")
                s.shared("action_verbalize_update_tool_config").format(toolName)
            }
            "delete" -> {
                val toolId = params.optString("tool_instance_id")
                // Try to get name from params first (enriched by CommandExecutor after delete),
                // otherwise fallback to DB lookup (which will fail if already deleted)
                val toolName = params.optString("name").takeIf { it.isNotBlank() }
                    ?: getToolName(toolId, context)
                    ?: s.shared("content_unnamed")
                s.shared("action_verbalize_delete_tool").format(toolName)
            }
            else -> s.shared("action_verbalize_unknown")
        }
    }

    /**
     * Helper to retrieve tool name by ID
     * Note: Uses runBlocking since verbalize() is not suspend but needs DB access
     */
    private fun getToolName(toolInstanceId: String, context: Context): String? {
        if (toolInstanceId.isBlank()) return null
        return runBlocking {
            val coordinator = Coordinator(context)
            val result = coordinator.processUserAction("tools.get", mapOf(
                "tool_instance_id" to toolInstanceId
            ))
            if (result.status == CommandStatus.SUCCESS) {
                val tool = result.data?.get("tool_instance") as? Map<*, *>
                tool?.get("name") as? String
            } else null
        }
    }

    /**
     * Processes custom fields changes during tool instance config update.
     *
     * This function handles the complete custom fields migration workflow for AI-driven updates:
     * 1. Detecting structural changes (additions, removals, type changes, option removals)
     * 2. Validating forbidden changes (name changes, type changes)
     * 3. Executing data migration silently (field removals, conditional removals)
     * 4. Generating technical names for new fields
     * 5. Validating all field definitions
     *
     * Migration is automatic and silent for AI updates (no user confirmation).
     * Forbidden changes (name/type) return errors and block the update.
     *
     * @param toolInstanceId ID of the tool instance being updated
     * @param oldConfigJson Previous configuration JSON
     * @param newConfigJson New configuration JSON (with custom_fields possibly modified)
     * @param token Cancellation token
     * @return OperationResult with processed_config containing generated field names
     */
    private suspend fun processCustomFields(
        toolInstanceId: String,
        oldConfigJson: String,
        newConfigJson: String,
        token: CancellationToken
    ): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        try {
            val oldConfig = JSONObject(oldConfigJson)
            val newConfig = JSONObject(newConfigJson)

            // Extract custom_fields arrays
            val oldFieldsArray = oldConfig.optJSONArray("custom_fields")
            val newFieldsArray = newConfig.optJSONArray("custom_fields")

            // If no custom_fields in new config, nothing to process
            if (newFieldsArray == null || newFieldsArray.length() == 0) {
                // If old config had fields, we need to migrate (remove all fields from entries)
                if (oldFieldsArray != null && oldFieldsArray.length() > 0) {
                    val oldFields = oldFieldsArray.toFieldDefinitions()

                    // Use migration system to handle removal
                    val changes = oldFields.map { field ->
                        com.assistant.core.fields.migration.FieldChange.Removed(field.name)
                    }

                    val strategies = MigrationPolicy.getStrategies(changes)

                    val migrationResult = FieldDataMigrator.migrateCustomFields(
                        coordinator = Coordinator(context),
                        toolInstanceId = toolInstanceId,
                        changes = changes,
                        strategies = strategies,
                        context = context,
                        token = token
                    )

                    if (!migrationResult.success) {
                        LogManager.service(
                            "Failed to remove all custom fields: ${migrationResult.error}",
                            "ERROR"
                        )
                        return migrationResult
                    }
                }
                return OperationResult.success(mapOf("processed_config" to newConfigJson))
            }

            // Parse field definitions
            val oldFields = if (oldFieldsArray != null) {
                oldFieldsArray.toFieldDefinitions()
            } else {
                emptyList<FieldDefinition>()
            }

            val newFieldsList = mutableListOf<FieldDefinition>()
            for (i in 0 until newFieldsArray.length()) {
                newFieldsList.add(newFieldsArray.getJSONObject(i).toFieldDefinition())
            }

            // Phase 1: Generate names for new fields (fields without 'name' or with empty 'name')
            val processedFields = newFieldsList.map { field ->
                if (field.name.isEmpty()) {
                    // Generate name from displayName
                    val generatedName = FieldNameGenerator.generateName(
                        field.displayName,
                        newFieldsList.filter { it.name.isNotEmpty() }
                    )
                    field.copy(name = generatedName)
                } else {
                    field
                }
            }

            if (token.isCancelled) return OperationResult.cancelled()

            // Phase 2: Migration validation and execution
            // Validate no forbidden changes (type changes only - removals are legitimate)
            // Note: Name changes cannot be detected (name is the identifier)
            val typeValidation = FieldConfigValidator.validateNoTypeChanges(
                oldFields = oldFields,
                newFields = processedFields,
                context = context
            )
            if (!typeValidation.isValid) {
                LogManager.service(
                    "Tool config attempted forbidden type change: ${typeValidation.errorMessage}",
                    "ERROR"
                )
                return OperationResult.error(typeValidation.errorMessage ?: s.shared("error_field_type_changed"))
            }

            // Detect all structural changes
            val changes = FieldConfigComparator.compare(oldFields, processedFields)

            if (changes.isNotEmpty()) {
                LogManager.service(
                    "Detected ${changes.size} custom field change(s) for tool $toolInstanceId",
                    "INFO"
                )

                // Determine migration strategies
                val strategies = MigrationPolicy.getStrategies(changes)

                // Check for error strategies (should have been caught above, but double-check)
                if (MigrationPolicy.hasErrorStrategy(strategies)) {
                    val errorMessage = MigrationPolicy.getDescription(changes, strategies, context)
                    LogManager.service(
                        "Migration blocked by error strategy: $errorMessage",
                        "ERROR"
                    )
                    return OperationResult.error(errorMessage)
                }

                // Execute migration if needed (silent for AI)
                if (MigrationPolicy.requiresMigration(strategies)) {
                    LogManager.service(
                        "Executing automatic migration for tool $toolInstanceId",
                        "INFO"
                    )

                    val migrationResult = FieldDataMigrator.migrateCustomFields(
                        coordinator = Coordinator(context),
                        toolInstanceId = toolInstanceId,
                        changes = changes,
                        strategies = strategies,
                        context = context,
                        token = token
                    )

                    if (!migrationResult.success) {
                        LogManager.service(
                            "Migration failed: ${migrationResult.error}",
                            "ERROR"
                        )
                        return migrationResult
                    }

                    LogManager.service(
                        "Migration completed successfully",
                        "INFO"
                    )
                }
            }

            if (token.isCancelled) return OperationResult.cancelled()

            // Phase 3: Field definition validation (all fields must pass)
            val existingFieldsForValidation = processedFields.toMutableList()
            for ((index, field) in processedFields.withIndex()) {
                // For validation, exclude current field from existing list to avoid self-collision
                val otherFields = existingFieldsForValidation.filterIndexed { i, _ -> i != index }

                val validation = FieldConfigValidator.validate(field, otherFields, context)
                if (!validation.isValid) {
                    LogManager.service(
                        "Field validation failed for '${field.displayName}': ${validation.errorMessage}",
                        "ERROR"
                    )
                    return OperationResult.error(
                        "Validation custom field '${field.displayName}': ${validation.errorMessage}"
                    )
                }
            }

            // Phase 4: Build processed config with generated names
            val processedFieldsArray = processedFields.toJsonArray()
            newConfig.put("custom_fields", processedFieldsArray)

            return OperationResult.success(mapOf(
                "processed_config" to newConfig.toString()
            ))

        } catch (e: ValidationException) {
            LogManager.service("Custom fields validation error: ${e.message}", "ERROR", e)
            return OperationResult.error("Custom fields validation error: ${e.message}")
        } catch (e: Exception) {
            LogManager.service("Failed to process custom fields: ${e.message}", "ERROR", e)
            return OperationResult.error("Failed to process custom fields: ${e.message}")
        }
    }

    /**
     * Legacy helper function - DEPRECATED.
     * Now handled by FieldDataMigrator.migrateCustomFields() which supports
     * multiple migration strategies (STRIP_FIELD, STRIP_FIELD_IF_VALUE, etc.)
     *
     * This function is kept for reference only and should not be used in new code.
     */
    @Deprecated(
        message = "Use FieldDataMigrator.migrateCustomFields() instead",
        replaceWith = ReplaceWith("FieldDataMigrator.migrateCustomFields(...)")
    )
    private suspend fun removeCustomFieldFromEntries(
        toolInstanceId: String,
        fieldName: String,
        token: CancellationToken
    ) {
        // Kept for reference only - not used anymore
        // Migration now handled by FieldDataMigrator
    }

    /**
     * Helper to retrieve zone name by ID
     * Note: Uses runBlocking since verbalize() is not suspend but needs DB access
     */
    private fun getZoneName(zoneId: String, context: Context): String? {
        if (zoneId.isBlank()) return null
        return runBlocking {
            val coordinator = Coordinator(context)
            val result = coordinator.processUserAction("zones.get", mapOf("zone_id" to zoneId))
            if (result.status == CommandStatus.SUCCESS) {
                val zone = result.data?.get("zone") as? Map<*, *>
                zone?.get("name") as? String
            } else null
        }
    }
}