package com.assistant.tools.tracking

import android.content.Context
import android.util.Log
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.OperationResult
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.validation.ValidationResult
import com.assistant.core.database.AppDatabase
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
    
    private val database by lazy { AppDatabase.getDatabase(context) }
    private val toolInstanceDao by lazy { database.toolInstanceDao() }
    
    // Temporary data storage for multi-step operations
    private val tempData = ConcurrentHashMap<String, Any>()
    
    /**
     * Extract properties from params JSONObject for specific tracking types
     * @param params The JSONObject containing the parameters
     * @param type The tracking type identifier
     * @return Map of properties specific to the tracking type
     */
    private fun extractPropertiesFromParams(params: JSONObject, type: String, instanceConfig: JSONObject?): Map<String, Any> {
        return when (type) {
            "numeric" -> mapOf(
                "quantity" to params.optString("quantity", ""),
                "unit" to params.optString("unit", "")
            )
            "boolean" -> mapOf(
                "state" to params.optBoolean("state", false),
                "true_label" to (instanceConfig?.optString("true_label", "Oui") ?: "Oui"),
                "false_label" to (instanceConfig?.optString("false_label", "Non") ?: "Non")
            )
            "scale" -> mapOf(
                "value" to params.optInt("value", 5),
                "min_value" to params.optInt("min_value", 1),
                "max_value" to params.optInt("max_value", 10),
                "min_label" to params.optString("min_label", ""),
                "max_label" to params.optString("max_label", "")
            )
            "text" -> mapOf(
                "text" to params.optString("text", "")
            )
            "choice" -> {
                // Get available options from instance config, not user params
                val availableOptions = instanceConfig?.optJSONArray("options")?.let { array ->
                    (0 until array.length()).map { array.optString(it, "") }.filter { it.isNotEmpty() }
                } ?: emptyList<String>()
                
                mapOf(
                    "available_options" to availableOptions,
                    "selected_option" to params.optString("selected_option", "")
                )
            }
            "counter" -> mapOf(
                "increment" to params.optInt("increment", 1),
                "unit" to (instanceConfig?.optString("unit", "") ?: "")
            )
            "timer" -> mapOf(
                "activity" to params.optString("activity", ""),
                "duration_minutes" to params.optInt("duration_minutes", 0)
            )
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
            name.isBlank()) {
            return OperationResult.error("Tool instance ID, zone name, tool instance name and name are required")
        }
        
        // Create JSON value using TrackingTypeFactory
        val handler = TrackingTypeFactory.getHandler(type)
        if (handler == null) {
            return OperationResult.error("Unsupported tracking type: $type")
        }
        
        // Get instance config for choice options and other type configs
        val instanceConfig = toolInstanceDao.getToolInstanceById(toolInstanceId)?.config_json?.let { JSONObject(it) }
        val properties = extractPropertiesFromParams(params, type, instanceConfig)
        android.util.Log.d("TRACKING_DEBUG", "Extracted properties for type $type: $properties")
        
        val valueJson = handler.createValueJson(properties)
        android.util.Log.d("TRACKING_DEBUG", "Created valueJson: $valueJson")
        
        if (valueJson == null) {
            android.util.Log.e("TRACKING_DEBUG", "FAILED to create valueJson for type $type with properties: $properties")
            return OperationResult.error("Failed to create value JSON for $type data: $properties")
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
        android.util.Log.d("TRACKING_DEBUG", "Starting validation for entry: ${newEntry.name}, type: ${newEntry.value}")
        if (toolType != null) {
            val validation = toolType.validateData(newEntry, "create")
            android.util.Log.d("TRACKING_DEBUG", "Validation result: isValid=${validation.isValid}, error=${validation.errorMessage}")
            if (!validation.isValid) {
                android.util.Log.e("TRACKING_DEBUG", "VALIDATION FAILED: ${validation.errorMessage}")
                return OperationResult.error("Validation failed: ${validation.errorMessage}")
            }
        } else {
            android.util.Log.e("TRACKING_DEBUG", "ToolType is NULL!")
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
        val recordedAt = params.optLong("recorded_at", -1)
        
        if (entryId.isBlank()) {
            return OperationResult.error("Entry ID is required")
        }
        
        val existingEntry = trackingDao.getEntryById(entryId)
            ?: return OperationResult.error("Tracking entry not found")
        
        // Extract type from existing entry value
        val type = try {
            val json = org.json.JSONObject(existingEntry.value)
            val typeFromJson = json.optString("type", "")
            if (typeFromJson.isBlank()) {
                return OperationResult.error("Cannot determine type from existing entry")
            }
            typeFromJson
        } catch (e: Exception) { 
            return OperationResult.error("Invalid JSON format in existing entry: ${e.message}")
        }
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        // Extract new properties from params and create new JSON value
        android.util.Log.d("TRACKING_DEBUG", "TrackingService.handleUpdate - extracting properties for type: $type from params: $params")
        
        // Create new JSON value using TrackingTypeFactory
        val handler = TrackingTypeFactory.getHandler(type)
        if (handler == null) {
            return OperationResult.error("Unsupported tracking type: $type")
        }
        
        // Get instance config for choice options and other type configs
        val instanceConfig = toolInstanceDao.getToolInstanceById(existingEntry.tool_instance_id)?.config_json?.let { JSONObject(it) }
        val properties = extractPropertiesFromParams(params, type, instanceConfig).toMutableMap()
        
        // Preserve existing properties that are not provided in update (generic approach)
        try {
            val existingJson = org.json.JSONObject(existingEntry.value)
            val existingProperties = TrackingToolType.jsonToProperties(existingJson, type)
            
            // For each existing property, use it if not provided in the new properties
            existingProperties.forEach { (key, value) ->
                if (!properties.containsKey(key) || properties[key].toString().isBlank()) {
                    properties[key] = value
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("TRACKING_DEBUG", "Could not preserve existing properties: ${e.message}")
        }
        
        android.util.Log.d("TRACKING_DEBUG", "TrackingService.handleUpdate - extracted properties: $properties")
        
        val valueJson = handler.createValueJson(properties)
        if (valueJson == null) {
            return OperationResult.error("Invalid $type data: $properties")
        }
        
        android.util.Log.d("TRACKING_DEBUG", "TrackingService.handleUpdate - created new valueJson: $valueJson")
        val newValue = valueJson
        
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