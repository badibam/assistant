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
     */
    private suspend fun handleUpdate(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("tool_instance_id")
        val configJson = params.optString("config_json")

        if (toolInstanceId.isBlank()) {
            return OperationResult.error(s.shared("service_error_tool_instance_id_required"))
        }

        val existingTool = toolInstanceDao.getToolInstanceById(toolInstanceId)
            ?: return OperationResult.error(s.shared("service_error_tool_instance_not_found"))

        if (token.isCancelled) return OperationResult.cancelled()

        val updatedTool = existingTool.copy(
            config_json = configJson.takeIf { it.isNotBlank() } ?: existingTool.config_json,
            updated_at = System.currentTimeMillis()
        )

        if (token.isCancelled) return OperationResult.cancelled()

        toolInstanceDao.updateToolInstance(updatedTool)

        // Notify UI of tools change in this zone
        DataChangeNotifier.notifyToolsChanged(updatedTool.zone_id)

        return OperationResult.success(mapOf(
            "tool_instance_id" to updatedTool.id,
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

        val toolInstances = toolInstanceDao.getAllToolInstances()
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceData = toolInstances.map { tool ->
            // Extract name and description from config JSON (not the full config)
            var name = ""
            var description = ""
            try {
                val configJson = JSONObject(tool.config_json)
                name = configJson.optString("name", "")
                description = configJson.optString("description", "")
            } catch (e: Exception) {
                // Keep empty strings
            }

            mapOf(
                "id" to tool.id,
                "zone_id" to tool.zone_id,
                "name" to name,
                "description" to description,
                "tool_type" to tool.tool_type,
                "config_json" to tool.config_json,
                "order_index" to tool.order_index,
                "created_at" to tool.created_at,
                "updated_at" to tool.updated_at
            )
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