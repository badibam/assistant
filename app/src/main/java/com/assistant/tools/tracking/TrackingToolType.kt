package com.assistant.tools.tracking

import com.assistant.tools.base.ToolType
import com.assistant.tools.base.ConfigValidationResult
import com.assistant.tools.base.OperationResult
import com.assistant.tools.tracking.data.TrackingRepository
import com.assistant.tools.tracking.entities.TrackingData
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.first

/**
 * Tracking Tool Type implementation
 * Handles quantitative and qualitative data tracking over time
 * Supports grouped items with flexible UI modes
 */
class TrackingToolType(
    private val repository: TrackingRepository
) : ToolType() {
    
    private val gson = Gson()
    
    override fun getDefaultConfig(): String {
        return """
        {
            "name": "",
            "type": "numeric",
            "show_value": true,
            "item_mode": "free",
            "save_new_items": false,
            "default_unit": "",
            "min_value": null,
            "max_value": null,
            "groups": [
                {
                    "name": "Default",
                    "items": []
                }
            ]
        }
        """.trimIndent()
    }
    
    override fun validateConfig(configJson: String): ConfigValidationResult {
        return try {
            val config = gson.fromJson(configJson, JsonObject::class.java)
            val errors = mutableListOf<String>()
            
            // Validate required fields
            if (!config.has("name") || config.get("name").asString.isBlank()) {
                errors.add("Name is required")
            }
            
            if (!config.has("type")) {
                errors.add("Type is required")
            } else {
                val type = config.get("type").asString
                if (type !in listOf("numeric", "text", "scale", "boolean")) {
                    errors.add("Type must be one of: numeric, text, scale, boolean")
                }
            }
            
            if (!config.has("item_mode")) {
                errors.add("Item mode is required")
            } else {
                val mode = config.get("item_mode").asString
                if (mode !in listOf("free", "predefined", "both")) {
                    errors.add("Item mode must be one of: free, predefined, both")
                }
            }
            
            // Validate groups structure - at least one group required
            if (!config.has("groups")) {
                errors.add("Groups are required")
            } else {
                val groups = config.getAsJsonArray("groups")
                if (groups.size() == 0) {
                    errors.add("At least one group is required")
                }
                
                groups.forEach { groupElement ->
                    val group = groupElement.asJsonObject
                    if (!group.has("name") || group.get("name").asString.isBlank()) {
                        errors.add("Group name is required")
                    }
                    if (group.has("items")) {
                        val items = group.getAsJsonArray("items")
                        items.forEach { itemElement ->
                            val item = itemElement.asJsonObject
                            if (!item.has("name") || item.get("name").asString.isBlank()) {
                                errors.add("Item name is required")
                            }
                        }
                    }
                }
            }
            
            if (errors.isEmpty()) {
                ConfigValidationResult.valid()
            } else {
                ConfigValidationResult.invalid(errors)
            }
        } catch (e: Exception) {
            ConfigValidationResult.invalid(listOf("Invalid JSON format: ${e.message}"))
        }
    }
    
    override suspend fun execute(operation: String, params: Map<String, Any>): OperationResult {
        return when (operation) {
            "add_entry" -> addEntry(params)
            "get_entries" -> getEntries(params)
            "update_entry" -> updateEntry(params)
            "delete_entry" -> deleteEntry(params)
            else -> OperationResult.failure("Unknown operation: $operation")
        }
    }
    
    override fun getAvailableOperations(): List<String> {
        return listOf("add_entry", "get_entries", "update_entry", "delete_entry")
    }
    
    private suspend fun addEntry(params: Map<String, Any>): OperationResult {
        return try {
            val instanceId = params["instance_id"] as? String
                ?: return OperationResult.failure("Missing instance_id")
            
            val name = params["name"] as? String
                ?: return OperationResult.failure("Missing name")
                
            val value = params["value"] as? String
                ?: return OperationResult.failure("Missing value")
            
            // Ensure group_name is provided, fallback to "Default"
            val groupName = params["group_name"] as? String ?: "Default"
            
            // TODO: Get zone_name and tool_instance_name from database
            val zoneName = params["zone_name"] as? String ?: "TODO: Load from DB"
            val instanceName = params["instance_name"] as? String ?: "TODO: Load from DB"
            
            val entry = TrackingData(
                tool_instance_id = instanceId,
                zone_name = zoneName,
                group_name = groupName,
                tool_instance_name = instanceName,
                name = name,
                value = value,
                recorded_at = params["recorded_at"] as? Long ?: System.currentTimeMillis()
            )
            
            repository.addEntry(entry)
            
            OperationResult.success(
                message = "Entry added successfully",
                data = mapOf("entry_id" to entry.id)
            )
        } catch (e: Exception) {
            OperationResult.failure("Failed to add entry: ${e.message}")
        }
    }
    
    private suspend fun getEntries(params: Map<String, Any>): OperationResult {
        return try {
            val instanceId = params["instance_id"] as? String
                ?: return OperationResult.failure("Missing instance_id")
            
            val entries = repository.getEntries(instanceId).first()
            
            OperationResult.success(
                data = mapOf(
                    "entries" to entries,
                    "count" to entries.size
                )
            )
        } catch (e: Exception) {
            OperationResult.failure("Failed to get entries: ${e.message}")
        }
    }
    
    private suspend fun updateEntry(params: Map<String, Any>): OperationResult {
        // TODO: Implement entry update
        return OperationResult.success("Update entry (TODO: implement)")
    }
    
    private suspend fun deleteEntry(params: Map<String, Any>): OperationResult {
        return try {
            val entryId = params["entry_id"] as? String
                ?: return OperationResult.failure("Missing entry_id")
            
            repository.deleteEntry(entryId)
            OperationResult.success("Entry deleted successfully")
        } catch (e: Exception) {
            OperationResult.failure("Failed to delete entry: ${e.message}")
        }
    }
}