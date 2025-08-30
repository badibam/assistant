package com.assistant.tools.tracking

import android.content.Context
import android.util.Log
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.OperationResult
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.validation.ValidationResult
import com.assistant.tools.tracking.data.TrackingDao
import com.assistant.tools.tracking.entities.TrackingData
import com.assistant.tools.tracking.TrackingUtils
import com.assistant.tools.tracking.handlers.TrackingTypeFactory
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
     * Extract properties from params JSONObject for specific tracking types
     * @param params The JSONObject containing the parameters
     * @param type The tracking type identifier
     * @return Map of properties specific to the tracking type
     */
    private fun extractPropertiesFromParams(params: JSONObject, type: String): Map<String, Any> {
        return when (type) {
            "numeric" -> mapOf(
                "quantity" to params.optString("quantity", ""),
                "unit" to params.optString("unit", "")
            )
            // Future types will be handled here:
            // "scale" -> mapOf("value" to params.optInt("value", 1))
            // "text" -> mapOf("text" to params.optString("text", ""))
            // "choice" -> mapOf("selected_option" to params.optString("selected_option", ""))
            // "boolean" -> mapOf("state" to params.optBoolean("state", false))
            // "counter" -> mapOf("increment" to params.optInt("increment", 1))
            // "timer" -> mapOf("duration_minutes" to params.optInt("duration_minutes", 0))
            else -> emptyMap()
        }
    }
    
    /**
     * Execute tracking operation with cancellation support
     */
    override suspend fun execute(
        operation: String, 
        params: JSONObject, 
        token: CancellationToken
    ): OperationResult {
        Log.d("TrackingService", "execute() called with operation: $operation, params: $params")
        return try {
            when (operation) {
                "create" -> handleCreate(params, token)
                "update" -> handleUpdate(params, token)
                "delete" -> handleDelete(params, token)
                "get_entries" -> handleGetEntries(params, token)
                "get_entries_by_date_range" -> handleGetEntriesByDateRange(params, token)
                "get_entry_by_id" -> handleGetEntryById(params, token)
                else -> {
                    Log.e("TrackingService", "Unknown operation: $operation")
                    OperationResult.error("Unknown tracking operation: $operation")
                }
            }
        } catch (e: Exception) {
            Log.e("TrackingService", "Operation failed", e)
            OperationResult.error("Tracking operation failed: ${e.message}")
        }
    }
    
    /**
     * Create a new tracking entry
     */
    private suspend fun handleCreate(params: JSONObject, token: CancellationToken): OperationResult {
        Log.d("TrackingService", "handleCreate() called with params: $params")
        if (token.isCancelled) return OperationResult.cancelled()
        
        val toolInstanceId = params.optString("tool_instance_id")
        val zoneName = params.optString("zone_name")
        val toolInstanceName = params.optString("tool_instance_name")
        val name = params.optString("name")
        val quantity = params.optString("quantity")
        val unit = params.optString("unit")
        val type = params.optString("type", "numeric")
        val recordedAt = params.optLong("recorded_at", System.currentTimeMillis())
        
        Log.d("TrackingService", "Creating entry: toolInstanceId=$toolInstanceId, name=$name, quantity=$quantity, unit=$unit")
        
        if (toolInstanceId.isBlank() || zoneName.isBlank() || toolInstanceName.isBlank() || 
            name.isBlank() || quantity.isBlank()) {
            return OperationResult.error("Tool instance ID, zone name, tool instance name, name and quantity are required")
        }
        
        // Create JSON value using TrackingTypeFactory
        val handler = TrackingTypeFactory.getHandler(type)
        if (handler == null) {
            return OperationResult.error("Unsupported tracking type: $type")
        }
        
        val properties = extractPropertiesFromParams(params, type)
        val valueJson = handler.createValueJson(properties)
        
        if (valueJson == null) {
            return OperationResult.error("Invalid $type data: $properties")
        }
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        val newEntry = TrackingData(
            tool_instance_id = toolInstanceId,
            zone_name = zoneName,
            tool_instance_name = toolInstanceName,
            name = name,
            value = valueJson,
            recorded_at = recordedAt
        )
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        // Validate data before insertion
        val toolType = ToolTypeManager.getToolType("tracking")
        if (toolType != null) {
            val validation = toolType.validateData(newEntry, "create")
            if (!validation.isValid) {
                Log.e("TrackingService", "Validation failed: ${validation.errorMessage}")
                return OperationResult.error("Validation failed: ${validation.errorMessage}")
            }
        }
        
        Log.d("TrackingService", "Inserting entry into database: ${newEntry.id}")
        trackingDao.insertEntry(newEntry)
        Log.d("TrackingService", "Entry inserted successfully")
        
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
        val quantity = params.optString("quantity")
        val type = params.optString("type", "numeric")
        val recordedAt = params.optLong("recorded_at", -1)
        
        if (entryId.isBlank()) {
            return OperationResult.error("Entry ID is required")
        }
        
        val existingEntry = trackingDao.getEntryById(entryId)
            ?: return OperationResult.error("Tracking entry not found")
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        // If quantity is provided, create new JSON value keeping existing unit
        var newValue = existingEntry.value
        if (quantity.isNotBlank()) {
            // Parse existing value to get unit
            val existingUnit = try {
                val json = org.json.JSONObject(existingEntry.value)
                json.optString("unit", "")
            } catch (e: Exception) { "" }
            
            // Create new JSON value using TrackingTypeFactory
            val handler = TrackingTypeFactory.getHandler(type)
            if (handler == null) {
                return OperationResult.error("Unsupported tracking type: $type")
            }
            
            val properties = extractPropertiesFromParams(params, type).toMutableMap()
            // For numeric type, preserve existing unit if not provided in update
            if (type == "numeric" && properties["unit"].toString().isBlank()) {
                properties["unit"] = existingUnit
            }
            
            val valueJson = handler.createValueJson(properties)
            if (valueJson == null) {
                return OperationResult.error("Invalid $type data: $properties")
            }
            
            newValue = valueJson
        }
        
        val updatedEntry = existingEntry.copy(
            value = newValue,
            recorded_at = if (recordedAt != -1L) recordedAt else existingEntry.recorded_at,
            updated_at = System.currentTimeMillis()
        )
        
        // Validate data before update
        val toolType = ToolTypeManager.getToolType("tracking")
        if (toolType != null) {
            val validation = toolType.validateData(updatedEntry, "update")
            if (!validation.isValid) {
                Log.e("TrackingService", "Update validation failed: ${validation.errorMessage}")
                return OperationResult.error("Validation failed: ${validation.errorMessage}")
            }
        }
        
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
        
        // Validate data before delete
        val toolType = ToolTypeManager.getToolType("tracking")
        if (toolType != null) {
            val validation = toolType.validateData(existingEntry, "delete")
            if (!validation.isValid) {
                Log.e("TrackingService", "Delete validation failed: ${validation.errorMessage}")
                return OperationResult.error("Validation failed: ${validation.errorMessage}")
            }
        }
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        trackingDao.deleteEntryById(entryId)
        
        return OperationResult.success(mapOf(
            "entry_id" to entryId,
            "deleted_at" to System.currentTimeMillis()
        ))
    }

    /**
     * Get all entries for a tool instance
     */
    private suspend fun handleGetEntries(params: JSONObject, token: CancellationToken): OperationResult {
        Log.d("TrackingService", "handleGetEntries() called with params: $params")
        if (token.isCancelled) return OperationResult.cancelled()
        
        val toolInstanceId = params.optString("tool_instance_id")
        Log.d("TrackingService", "Tool instance ID: $toolInstanceId")
        if (toolInstanceId.isBlank()) {
            Log.e("TrackingService", "Tool instance ID is blank")
            return OperationResult.error("Tool instance ID is required")
        }
        
        Log.d("TrackingService", "Calling DAO.getEntriesByInstance...")
        val entries = trackingDao.getEntriesByInstance(toolInstanceId)
        Log.d("TrackingService", "DAO returned ${entries.size} entries")
        if (token.isCancelled) return OperationResult.cancelled()
        
        val entriesData = entries.map { entry ->
            mapOf(
                "id" to entry.id,
                "tool_instance_id" to entry.tool_instance_id,
                "zone_name" to entry.zone_name,
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