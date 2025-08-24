package com.assistant.core.services

import android.content.Context
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.tools.ToolTypeManager
import com.assistant.tools.tracking.data.TrackingDao
import com.assistant.tools.tracking.entities.TrackingData
import org.json.JSONObject
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracking Service - Service for tracking data operations
 * Implements the standard service pattern with cancellation token
 */
class TrackingService(private val context: Context) : ExecutableService {
    private val trackingDao by lazy { 
        ToolTypeManager.getDaoForToolType("tracking", context) as? TrackingDao
            ?: throw IllegalStateException("TrackingDao not available")
    }
    
    // Temporary data storage for multi-step operations
    private val tempData = ConcurrentHashMap<String, Any>()
    
    /**
     * Execute tracking operation with cancellation support
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
                "get_entries" -> handleGetEntries(params, token)
                "get_entries_by_date_range" -> handleGetEntriesByDateRange(params, token)
                "get_entry_by_id" -> handleGetEntryById(params, token)
                "analyze_correlation" -> handleCorrelationAnalysis(params, token)
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
    
    /**
     * Handle correlation analysis - example of multi-step operation
     * Phase 1: Load data, Phase 2: Heavy calculation, Phase 3: Save results
     */
    private suspend fun handleCorrelationAnalysis(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val operationId = params.optString("operationId")
        val phase = params.optInt("phase", 1)
        val toolInstanceId = params.optString("tool_instance_id")
        
        if (operationId.isBlank()) {
            return OperationResult.error("Operation ID is required for multi-step operations")
        }
        
        return when (phase) {
            1 -> handleCorrelationPhase1(operationId, toolInstanceId, token)
            2 -> handleCorrelationPhase2(operationId, token)
            3 -> handleCorrelationPhase3(operationId, token)
            else -> OperationResult.error("Invalid phase: $phase")
        }
    }
    
    /**
     * Phase 1: Load tracking data from database
     */
    private suspend fun handleCorrelationPhase1(operationId: String, toolInstanceId: String, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        if (toolInstanceId.isBlank()) {
            return OperationResult.error("Tool instance ID is required")
        }
        
        // Simulate loading data from database
        val entries = trackingDao.getEntriesByInstance(toolInstanceId)
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        // Store data for next phase
        tempData[operationId] = mapOf(
            "entries" to entries,
            "tool_instance_id" to toolInstanceId,
            "loaded_at" to System.currentTimeMillis()
        )
        
        // Return result indicating we need background processing
        return OperationResult.success(
            data = mapOf("phase" to 1, "entries_count" to entries.size),
            requiresBackground = true
        )
    }
    
    /**
     * Phase 2: Heavy correlation calculation (background)
     */
    private suspend fun handleCorrelationPhase2(operationId: String, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val storedData = tempData[operationId] as? Map<String, Any>
            ?: return OperationResult.error("No data found for operation $operationId")
        
        @Suppress("UNCHECKED_CAST")
        val entries = storedData["entries"] as? List<TrackingData>
            ?: return OperationResult.error("Invalid data format")
        
        // Simulate heavy calculation
        delay(3000) // 3 second calculation
        
        if (token.isCancelled) {
            tempData.remove(operationId) // Cleanup on cancel
            return OperationResult.cancelled()
        }
        
        // Simulate correlation calculation result
        val correlationResult = entries.size * 0.85 // Mock correlation value
        
        // Update stored data with result
        tempData[operationId] = storedData + mapOf(
            "correlation_result" to correlationResult,
            "calculated_at" to System.currentTimeMillis()
        )
        
        // Return result indicating we need continuation
        return OperationResult.success(
            data = mapOf("phase" to 2, "correlation" to correlationResult),
            requiresContinuation = true
        )
    }
    
    /**
     * Phase 3: Save correlation results to database
     */
    private suspend fun handleCorrelationPhase3(operationId: String, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val storedData = tempData[operationId] as? Map<String, Any>
            ?: return OperationResult.error("No data found for operation $operationId")
        
        val correlationResult = storedData["correlation_result"] as? Double
            ?: return OperationResult.error("No correlation result found")
        
        val toolInstanceId = storedData["tool_instance_id"] as? String
            ?: return OperationResult.error("No tool instance ID found")
        
        if (token.isCancelled) {
            tempData.remove(operationId) // Cleanup on cancel
            return OperationResult.cancelled()
        }
        
        // Create a special tracking entry to store the correlation result
        val correlationEntry = TrackingData(
            tool_instance_id = toolInstanceId,
            zone_name = "Statistics",
            group_name = "Correlations",
            tool_instance_name = "Auto-generated",
            name = "correlation_analysis",
            value = correlationResult.toString(),
            recorded_at = System.currentTimeMillis()
        )
        
        trackingDao.insertEntry(correlationEntry)
        
        // Cleanup temporary data
        tempData.remove(operationId)
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        return OperationResult.success(mapOf(
            "phase" to 3,
            "correlation_entry_id" to correlationEntry.id,
            "correlation_value" to correlationResult,
            "completed_at" to System.currentTimeMillis()
        ))
    }

    /**
     * Get all entries for a tool instance
     */
    private suspend fun handleGetEntries(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val toolInstanceId = params.optString("tool_instance_id")
        if (toolInstanceId.isBlank()) {
            return OperationResult.error("Tool instance ID is required")
        }
        
        val entries = trackingDao.getEntriesByInstance(toolInstanceId)
        if (token.isCancelled) return OperationResult.cancelled()
        
        val entriesData = entries.map { entry ->
            mapOf(
                "id" to entry.id,
                "tool_instance_id" to entry.tool_instance_id,
                "zone_name" to entry.zone_name,
                "group_name" to entry.group_name,
                "tool_instance_name" to entry.tool_instance_name,
                "name" to entry.name,
                "value" to entry.value,
                "recorded_at" to entry.recorded_at,
                "created_at" to entry.created_at,
                "updated_at" to entry.updated_at
            )
        }
        
        return OperationResult.success(mapOf(
            "entries" to entriesData,
            "count" to entriesData.size
        ))
    }

    /**
     * Get entries by date range
     */
    private suspend fun handleGetEntriesByDateRange(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val toolInstanceId = params.optString("tool_instance_id")
        val startTime = params.optLong("start_time", 0L)
        val endTime = params.optLong("end_time", System.currentTimeMillis())
        
        if (toolInstanceId.isBlank()) {
            return OperationResult.error("Tool instance ID is required")
        }
        
        val entries = trackingDao.getEntriesByDateRange(toolInstanceId, startTime, endTime)
        if (token.isCancelled) return OperationResult.cancelled()
        
        val entriesData = entries.map { entry ->
            mapOf(
                "id" to entry.id,
                "tool_instance_id" to entry.tool_instance_id,
                "zone_name" to entry.zone_name,
                "group_name" to entry.group_name,
                "tool_instance_name" to entry.tool_instance_name,
                "name" to entry.name,
                "value" to entry.value,
                "recorded_at" to entry.recorded_at,
                "created_at" to entry.created_at,
                "updated_at" to entry.updated_at
            )
        }
        
        return OperationResult.success(mapOf(
            "entries" to entriesData,
            "count" to entriesData.size,
            "start_time" to startTime,
            "end_time" to endTime
        ))
    }

    /**
     * Get single entry by ID
     */
    private suspend fun handleGetEntryById(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val entryId = params.optString("entry_id")
        if (entryId.isBlank()) {
            return OperationResult.error("Entry ID is required")
        }
        
        val entry = trackingDao.getEntryById(entryId)
            ?: return OperationResult.error("Entry not found")
        
        return OperationResult.success(mapOf(
            "entry" to mapOf(
                "id" to entry.id,
                "tool_instance_id" to entry.tool_instance_id,
                "zone_name" to entry.zone_name,
                "group_name" to entry.group_name,
                "tool_instance_name" to entry.tool_instance_name,
                "name" to entry.name,
                "value" to entry.value,
                "recorded_at" to entry.recorded_at,
                "created_at" to entry.created_at,
                "updated_at" to entry.updated_at
            )
        ))
    }
}