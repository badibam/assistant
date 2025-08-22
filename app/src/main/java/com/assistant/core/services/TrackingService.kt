package com.assistant.core.services

import android.content.Context
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.tools.ToolTypeManager
import com.assistant.tools.tracking.data.TrackingDao
import com.assistant.tools.tracking.entities.TrackingData
import org.json.JSONObject

/**
 * Tracking Service - Service for tracking data operations
 * Implements the standard service pattern with cancellation token
 */
class TrackingService(private val context: Context) {
    private val trackingDao by lazy { 
        ToolTypeManager.getDaoForToolType("tracking", context) as? TrackingDao
            ?: throw IllegalStateException("TrackingDao not available")
    }
    
    /**
     * Execute tracking operation with cancellation support
     */
    suspend fun execute(
        operation: String, 
        params: JSONObject, 
        token: CancellationToken
    ): OperationResult {
        return try {
            when (operation) {
                "create" -> handleCreate(params, token)
                "update" -> handleUpdate(params, token)
                "delete" -> handleDelete(params, token)
                else -> OperationResult.error("Unknown tracking operation: $operation")
            }
        } catch (e: Exception) {
            OperationResult.error("Tracking operation failed: ${e.message}")
        }
    }
    
    /**
     * Create a new tracking entry
     */
    private suspend fun handleCreate(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val toolInstanceId = params.optString("tool_instance_id")
        val zoneName = params.optString("zone_name")
        val groupName = params.optString("group_name").takeIf { it.isNotBlank() }
        val toolInstanceName = params.optString("tool_instance_name")
        val name = params.optString("name")
        val value = params.optString("value")
        val recordedAt = params.optLong("recorded_at", System.currentTimeMillis())
        
        if (toolInstanceId.isBlank() || zoneName.isBlank() || toolInstanceName.isBlank() || 
            name.isBlank() || value.isBlank()) {
            return OperationResult.error("Tool instance ID, zone name, tool instance name, name and value are required")
        }
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        val newEntry = TrackingData(
            tool_instance_id = toolInstanceId,
            zone_name = zoneName,
            group_name = groupName,
            tool_instance_name = toolInstanceName,
            name = name,
            value = value,
            recorded_at = recordedAt
        )
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        trackingDao.insertEntry(newEntry)
        
        return OperationResult.success(mapOf(
            "entry_id" to newEntry.id,
            "tool_instance_id" to newEntry.tool_instance_id,
            "recorded_at" to newEntry.recorded_at
        ))
    }
    
    /**
     * Update existing tracking entry
     */
    private suspend fun handleUpdate(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val entryId = params.optString("entry_id")
        val value = params.optString("value")
        val name = params.optString("name")
        val recordedAt = params.optLong("recorded_at", -1)
        
        if (entryId.isBlank()) {
            return OperationResult.error("Entry ID is required")
        }
        
        val existingEntry = trackingDao.getEntryById(entryId)
            ?: return OperationResult.error("Tracking entry not found")
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        val updatedEntry = existingEntry.copy(
            name = name.takeIf { it.isNotBlank() } ?: existingEntry.name,
            value = value.takeIf { it.isNotBlank() } ?: existingEntry.value,
            recorded_at = if (recordedAt != -1L) recordedAt else existingEntry.recorded_at,
            updated_at = System.currentTimeMillis()
        )
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        trackingDao.updateEntry(updatedEntry)
        
        return OperationResult.success(mapOf(
            "entry_id" to updatedEntry.id,
            "updated_at" to updatedEntry.updated_at
        ))
    }
    
    /**
     * Delete tracking entry
     */
    private suspend fun handleDelete(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val entryId = params.optString("entry_id")
        
        if (entryId.isBlank()) {
            return OperationResult.error("Entry ID is required")
        }
        
        val existingEntry = trackingDao.getEntryById(entryId)
            ?: return OperationResult.error("Tracking entry not found")
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        trackingDao.deleteEntryById(entryId)
        
        return OperationResult.success(mapOf(
            "entry_id" to entryId,
            "deleted_at" to System.currentTimeMillis()
        ))
    }
}