package com.assistant.tools.tracking

import android.content.Context
import android.util.Log
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.OperationResult
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.validation.ValidationResult
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.database.AppDatabase
import com.assistant.tools.tracking.data.TrackingDao
import com.assistant.tools.tracking.entities.TrackingData
import com.assistant.tools.tracking.TrackingUtils
import com.assistant.tools.tracking.handlers.TrackingTypeFactory
import com.assistant.tools.tracking.TrackingToolType.toValidationJson
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
     * Extract properties from value JSONObject for specific tracking types
     * @param valueJson The JSONObject containing the tracking value data
     * @param type The tracking type identifier
     * @return Map of properties specific to the tracking type
     */
    private fun extractPropertiesFromValueJson(valueJson: JSONObject, type: String, instanceConfig: JSONObject?): Map<String, Any> {
        return when (type) {
            "numeric" -> mapOf(
                "quantity" to valueJson.optString("quantity", ""),
                "unit" to valueJson.optString("unit", "")
            )
            "boolean" -> mapOf(
                "state" to valueJson.optBoolean("state", false),
                "true_label" to (instanceConfig?.optString("true_label", "Oui") ?: "Oui"),
                "false_label" to (instanceConfig?.optString("false_label", "Non") ?: "Non")
            )
            "scale" -> mapOf(
                "value" to valueJson.optInt("rating", 5), // Note: rating in JSON, value in properties
                "min_value" to valueJson.optInt("min_value", 1),
                "max_value" to valueJson.optInt("max_value", 10),
                "min_label" to valueJson.optString("min_label", ""),
                "max_label" to valueJson.optString("max_label", "")
            )
            "text" -> mapOf(
                "text" to valueJson.optString("text", "")
            )
            "choice" -> {
                // Get available options from instance config, not user params
                val availableOptions = instanceConfig?.optJSONArray("options")?.let { array ->
                    (0 until array.length()).map { array.optString(it, "") }.filter { it.isNotEmpty() }
                } ?: emptyList<String>()
                
                mapOf(
                    "available_options" to availableOptions,
                    "selected_option" to valueJson.optString("selected_option", "")
                )
            }
            "counter" -> mapOf(
                "increment" to valueJson.optInt("increment", 1),
                "unit" to (instanceConfig?.optString("unit", "") ?: "")
            )
            "timer" -> mapOf(
                "activity" to valueJson.optString("activity", ""),
                "duration_minutes" to valueJson.optInt("duration_minutes", 0)
            )
            else -> emptyMap()
        }
    }

    /**
     * Extract properties from params JSONObject for specific tracking types (LEGACY - for compatibility)
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
        android.util.Log.d("VALDEBUG", "=== TRACKINGSERVICE.HANDLECREATE START ===")
        android.util.Log.d("VALDEBUG", "Create params received: $params")
        Log.d("TrackingService", "handleCreate() called with params: $params")
        if (token.isCancelled) return OperationResult.cancelled()
        
        android.util.Log.d("VALDEBUG", "Extracting parameters...")
        val toolInstanceId = params.optString("tool_instance_id")
        val zoneName = params.optString("zone_name")
        val toolInstanceName = params.optString("tool_instance_name")
        val name = params.optString("name")
        val quantity = params.optString("quantity")
        val unit = params.optString("unit")
        val type = params.optString("type", "numeric")
        android.util.Log.d("VALDEBUG", "Parameters extracted: toolInstanceId=$toolInstanceId, name=$name, type=$type")
        // Parse date and time parameters or use current timestamp as fallback
        android.util.Log.d("VALDEBUG", "Parsing recorded_at timestamp...")
        val recordedAt = if (params.has("date") && params.has("time")) {
            try {
                val date = params.optString("date")
                val time = params.optString("time")
                android.util.Log.d("VALDEBUG", "Converting date=$date time=$time to timestamp")
                val timestamp = com.assistant.core.utils.DateUtils.combineDateTime(date, time)
                android.util.Log.d("VALDEBUG", "Date conversion successful: $timestamp")
                timestamp
            } catch (e: Exception) {
                android.util.Log.e("VALDEBUG", "Failed to parse date/time, using current timestamp", e)
                System.currentTimeMillis()
            }
        } else {
            val timestamp = params.optLong("recorded_at", System.currentTimeMillis())
            android.util.Log.d("VALDEBUG", "Using provided/current timestamp: $timestamp")
            timestamp
        }
        
        android.util.Log.d("VALDEBUG", "Validating required parameters...")
        
        if (toolInstanceId.isBlank() || zoneName.isBlank() || toolInstanceName.isBlank() || 
            name.isBlank()) {
            android.util.Log.e("VALDEBUG", "Required parameters missing: toolInstanceId=$toolInstanceId, zoneName=$zoneName, toolInstanceName=$toolInstanceName, name=$name")
            return OperationResult.error("Tool instance ID, zone name, tool instance name and name are required")
        }
        
        android.util.Log.d("VALDEBUG", "Getting handler for type: $type")
        // Create JSON value using TrackingTypeFactory
        val handler = TrackingTypeFactory.getHandler(type)
        if (handler == null) {
            android.util.Log.e("VALDEBUG", "No handler found for tracking type: $type")
            return OperationResult.error("Unsupported tracking type: $type")
        }
        android.util.Log.d("VALDEBUG", "Handler retrieved successfully for type: $type")
        
        android.util.Log.d("VALDEBUG", "Getting instance config from DB...")
        // Get instance config for choice options and other type configs
        val instanceConfig = toolInstanceDao.getToolInstanceById(toolInstanceId)?.config_json?.let { JSONObject(it) }
        android.util.Log.d("VALDEBUG", "Instance config retrieved: ${instanceConfig != null}")
        
        android.util.Log.d("VALDEBUG", "Extracting properties from params...")
        
        // Extract value JSON and parse it to get the actual data
        val valueJsonString = params.optString("value", "{}")
        android.util.Log.d("VALDEBUG", "Value JSON string from params: $valueJsonString")
        
        val valueJson = try { 
            JSONObject(valueJsonString) 
        } catch (e: Exception) { 
            android.util.Log.e("VALDEBUG", "Failed to parse value JSON: $valueJsonString", e)
            JSONObject() 
        }
        
        val properties = extractPropertiesFromValueJson(valueJson, type, instanceConfig)
        android.util.Log.d("VALDEBUG", "Properties extracted: $properties")
        
        android.util.Log.d("VALDEBUG", "Creating value JSON from properties...")
        val createdValueJson = handler.createValueJson(properties)
        android.util.Log.d("VALDEBUG", "Created valueJson: $createdValueJson")
        
        if (createdValueJson == null) {
            android.util.Log.e("VALDEBUG", "FAILED to create valueJson for type $type with properties: $properties")
            return OperationResult.error("Failed to create value JSON for $type data: $properties")
        }
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        val newEntry = TrackingData(
            tool_instance_id = toolInstanceId,
            zone_name = zoneName,
            tool_instance_name = toolInstanceName,
            name = name,
            value = createdValueJson,
            recorded_at = recordedAt
        )
        
        android.util.Log.d("VALDEBUG", "newEntry created: ${newEntry.id}, name=${newEntry.name}, value=${newEntry.value}")
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        // Validate data before insertion using JSON Schema
        android.util.Log.d("VALDEBUG", "Starting validation phase...")
        val toolType = ToolTypeManager.getToolType("tracking")
        android.util.Log.d("VALDEBUG", "toolType retrieved: ${toolType != null}")
        if (toolType != null) {
            // Phase 1: Use new JSON Schema validation as primary validation
            try {
                android.util.Log.d("VALDEBUG", "Converting newEntry to validation JSON...")
                val dataJson = newEntry.toValidationJson()
                android.util.Log.d("VALDEBUG", "dataJson for validation: $dataJson")
                
                android.util.Log.d("VALDEBUG", "Calling SchemaValidator.validate...")
                val schemaValidation = SchemaValidator.validate(toolType, dataJson.let { 
                    // Convert JSON string to Map for new API
                    val jsonObject = JSONObject(it)
                    jsonObject.keys().asSequence().associateWith { key -> jsonObject.get(key) }
                }, useDataSchema = true)
                android.util.Log.d("VALDEBUG", "Schema validation result: isValid=${schemaValidation.isValid}, error=${schemaValidation.errorMessage}")
                
                // Use schema validation as primary validation
                if (!schemaValidation.isValid) {
                    android.util.Log.e("VALDEBUG", "Schema validation FAILED: ${schemaValidation.errorMessage}")
                    return OperationResult.error("Validation failed: ${schemaValidation.errorMessage}")
                }
                
                android.util.Log.d("VALDEBUG", "Validation phase completed successfully")
            } catch (e: Exception) {
                android.util.Log.e("VALDEBUG", "Exception during validation: ${e.message}", e)
                return OperationResult.error("Validation exception: ${e.message}")
            }
        } else {
            android.util.Log.e("VALDEBUG", "ToolType is NULL!")
        }
        
        android.util.Log.d("VALDEBUG", "Calling trackingDao.insertEntry...")
        trackingDao.insertEntry(newEntry)
        android.util.Log.d("VALDEBUG", "trackingDao.insertEntry completed successfully")
        
        val result = OperationResult.success(mapOf(
            "entry_id" to newEntry.id,
            "tool_instance_id" to newEntry.tool_instance_id,
            "recorded_at" to newEntry.recorded_at
        ))
        
        android.util.Log.d("VALDEBUG", "=== TRACKINGSERVICE.HANDLECREATE END - SUCCESS ===")
        return result
    }
    
    /**
     * Update existing tracking entry
     */
    private suspend fun handleUpdate(params: JSONObject, token: CancellationToken): OperationResult {
        android.util.Log.d("VALDEBUG", "=== TRACKINGSERVICE.HANDLEUPDATE START ===")
        android.util.Log.d("VALDEBUG", "Update params received: $params")
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
            val existingProperties = jsonToProperties(existingJson, type)
            
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
            name = params.optString("name", existingEntry.name),
            value = newValue,
            recorded_at = if (recordedAt != -1L) recordedAt else existingEntry.recorded_at,
            updated_at = System.currentTimeMillis()
        )
        
        android.util.Log.d("VALDEBUG", "updatedEntry created: name=${updatedEntry.name}, value=${updatedEntry.value}")
        
        // Validate data before update using JSON Schema
        android.util.Log.d("VALDEBUG", "Starting validation phase...")
        val toolType = ToolTypeManager.getToolType("tracking")
        android.util.Log.d("VALDEBUG", "toolType retrieved: ${toolType != null}")
        if (toolType != null) {
            // Phase 1: Use new JSON Schema validation as primary validation
            try {
                android.util.Log.d("VALDEBUG", "Converting updatedEntry to validation JSON...")
                val dataJson = updatedEntry.toValidationJson()
                android.util.Log.d("VALDEBUG", "dataJson for validation: $dataJson")
                
                android.util.Log.d("VALDEBUG", "Calling SchemaValidator.validate...")
                val schemaValidation = SchemaValidator.validate(toolType, dataJson.let { 
                    // Convert JSON string to Map for new API
                    val jsonObject = JSONObject(it)
                    jsonObject.keys().asSequence().associateWith { key -> jsonObject.get(key) }
                }, useDataSchema = true)
                android.util.Log.d("VALDEBUG", "Schema validation result: isValid=${schemaValidation.isValid}, error=${schemaValidation.errorMessage}")
                
                // Use schema validation as primary validation
                if (!schemaValidation.isValid) {
                    android.util.Log.e("VALDEBUG", "Schema validation FAILED: ${schemaValidation.errorMessage}")
                    return OperationResult.error("Validation failed: ${schemaValidation.errorMessage}")
                }
                
                android.util.Log.d("VALDEBUG", "Validation phase completed successfully")
            } catch (e: Exception) {
                android.util.Log.e("VALDEBUG", "Exception during validation: ${e.message}", e)
                return OperationResult.error("Validation exception: ${e.message}")
            }
        }
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        android.util.Log.d("VALDEBUG", "Calling trackingDao.updateEntry...")
        trackingDao.updateEntry(updatedEntry)
        android.util.Log.d("VALDEBUG", "trackingDao.updateEntry completed successfully")
        
        val result = OperationResult.success(mapOf(
            "entry_id" to updatedEntry.id,
            "updated_at" to updatedEntry.updated_at
        ))
        
        android.util.Log.d("VALDEBUG", "=== TRACKINGSERVICE.HANDLEUPDATE END - SUCCESS ===")
        return result
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
        
        // Validate data before delete using JSON Schema
        val toolType = ToolTypeManager.getToolType("tracking")
        if (toolType != null) {
            // Phase 1: Use new JSON Schema validation as primary validation
            val dataJson = existingEntry.toValidationJson()
            val schemaValidation = SchemaValidator.validate(toolType, dataJson.let { 
                // Convert JSON string to Map for new API
                val jsonObject = JSONObject(it)
                jsonObject.keys().asSequence().associateWith { key -> jsonObject.get(key) }
            }, useDataSchema = true)
            
            // Use schema validation as primary validation
            if (!schemaValidation.isValid) {
                Log.e("TrackingService", "Delete schema validation failed: ${schemaValidation.errorMessage}")
                return OperationResult.error("Validation failed: ${schemaValidation.errorMessage}")
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
     * Temporary utility to extract properties from JSON for update operations
     * TODO: Replace with UniversalDataMapper in future refactor
     */
    private fun jsonToProperties(json: JSONObject, type: String): Map<String, Any> {
        return when (type) {
            "numeric" -> mapOf(
                "quantity" to json.optString("quantity", ""),
                "unit" to json.optString("unit", "")
            )
            "boolean" -> mapOf(
                "state" to json.optBoolean("state", false),
                "true_label" to json.optString("true_label", "Oui"),
                "false_label" to json.optString("false_label", "Non")
            )
            "scale" -> mapOf(
                "rating" to json.optInt("rating", 0),
                "min_value" to json.optInt("min_value", 1),
                "max_value" to json.optInt("max_value", 10),
                "min_label" to json.optString("min_label", ""),
                "max_label" to json.optString("max_label", "")
            )
            "text" -> mapOf(
                "text" to json.optString("text", "")
            )
            "choice" -> {
                val availableOptions = json.optJSONArray("available_options")?.let { array ->
                    (0 until array.length()).map { array.optString(it, "") }.filter { it.isNotEmpty() }
                } ?: emptyList<String>()
                
                mapOf(
                    "selected_option" to json.optString("selected_option", ""),
                    "available_options" to availableOptions
                )
            }
            "counter" -> mapOf(
                "increment" to json.optInt("increment", 0)
            )
            "timer" -> mapOf(
                "activity" to json.optString("activity", ""),
                "duration_minutes" to json.optInt("duration_minutes", 0)
            )
            else -> emptyMap()
        }
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