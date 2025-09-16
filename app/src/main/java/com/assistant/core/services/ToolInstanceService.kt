package com.assistant.core.services

import android.content.Context
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
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
        val configMetadataJson = params.optString("config_metadata_json", "{}")
        
        if (zoneId.isBlank() || toolType.isBlank()) {
            return OperationResult.error(s.shared("service_error_zone_id_tool_type_required"))
        }
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        val newToolInstance = ToolInstance(
            zone_id = zoneId,
            tool_type = toolType,
            config_json = configJson,
            config_metadata_json = configMetadataJson
        )
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        toolInstanceDao.insertToolInstance(newToolInstance)
        
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
        val configMetadataJson = params.optString("config_metadata_json")
        
        if (toolInstanceId.isBlank()) {
            return OperationResult.error(s.shared("service_error_tool_instance_id_required"))
        }
        
        val existingTool = toolInstanceDao.getToolInstanceById(toolInstanceId)
            ?: return OperationResult.error(s.shared("service_error_tool_instance_not_found"))
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        val updatedTool = existingTool.copy(
            config_json = configJson.takeIf { it.isNotBlank() } ?: existingTool.config_json,
            config_metadata_json = configMetadataJson.takeIf { it.isNotBlank() } ?: existingTool.config_metadata_json,
            updated_at = System.currentTimeMillis()
        )
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        toolInstanceDao.updateToolInstance(updatedTool)
        
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
        
        toolInstanceDao.deleteToolInstanceById(toolInstanceId)
        
        return OperationResult.success(mapOf(
            "tool_instance_id" to toolInstanceId,
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
        
        val toolInstances = toolInstanceDao.getToolInstancesByZone(zoneId)
        if (token.isCancelled) return OperationResult.cancelled()
        
        val toolInstanceData = toolInstances.map { tool ->
            mapOf(
                "id" to tool.id,
                "zone_id" to tool.zone_id,
                "tool_type" to tool.tool_type,
                "config_json" to tool.config_json,
                "config_metadata_json" to tool.config_metadata_json,
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
        
        return OperationResult.success(mapOf(
            "tool_instance" to mapOf(
                "id" to toolInstance.id,
                "zone_id" to toolInstance.zone_id,
                "tool_type" to toolInstance.tool_type,
                "config_json" to toolInstance.config_json,
                "config_metadata_json" to toolInstance.config_metadata_json,
                "order_index" to toolInstance.order_index,
                "created_at" to toolInstance.created_at,
                "updated_at" to toolInstance.updated_at
            )
        ))
    }
}