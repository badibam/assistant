package com.assistant.core.services

import android.content.Context
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.Zone
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.utils.DataChangeNotifier
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray

/**
 * Zone Service - Core service for zone operations
 * Implements the standard service pattern with cancellation token
 */
class ZoneService(private val context: Context) : ExecutableService {
    private val database by lazy { AppDatabase.getDatabase(context) }
    private val zoneDao by lazy { database.zoneDao() }
    private val s = Strings.`for`(context = context)
    
    /**
     * Execute zone operation with cancellation support
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
                "get" -> handleGet(params, token)
                "list" -> handleList(params, token)
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        } catch (e: Exception) {
            OperationResult.error(s.shared("service_error_zone_service").format(e.message ?: ""))
        }
    }
    
    /**
     * Create a new zone
     */
    private suspend fun handleCreate(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val name = params.optString("name")
        if (name.isBlank()) {
            return OperationResult.error(s.shared("service_error_zone_name_required"))
        }

        val description = params.optString("description").takeIf { it.isNotBlank() }

        // Parse tool_groups if provided (validation already done by ActionValidator/ValidationHelper)
        val toolGroupsJson = if (params.has("tool_groups")) {
            val toolGroupsValue = params.opt("tool_groups")
            when (toolGroupsValue) {
                is JSONArray -> toolGroupsValue.toString()
                is String -> toolGroupsValue
                else -> null
            }
        } else {
            null
        }

        // Parse group if provided (zone group assignment for MainScreen organization)
        val group = params.optString("group").takeIf { it.isNotBlank() }

        com.assistant.core.utils.LogManager.service("ZoneService.handleCreate - params has group: ${params.has("group")}, group value: '$group'", "DEBUG")

        // Get current max order_index for proper ordering
        if (token.isCancelled) return OperationResult.cancelled()

        // For now, use simple ordering - could be enhanced later
        val orderIndex = System.currentTimeMillis().toInt() % 1000

        val newZone = Zone(
            name = name,
            description = description,
            order_index = orderIndex,
            tool_groups = toolGroupsJson,
            group = group
        )

        com.assistant.core.utils.LogManager.service("ZoneService.handleCreate - Created zone with group: '${newZone.group}'", "DEBUG")

        if (token.isCancelled) return OperationResult.cancelled()

        zoneDao.insertZone(newZone)

        // Notify UI of zones change
        DataChangeNotifier.notifyZonesChanged()

        return OperationResult.success(mapOf(
            "zone_id" to newZone.id,
            "name" to newZone.name,
            "created_at" to newZone.created_at
        ))
    }
    
    /**
     * Update existing zone
     */
    private suspend fun handleUpdate(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val zoneId = params.optString("zone_id")
        if (zoneId.isBlank()) {
            return OperationResult.error(s.shared("service_error_zone_id_required"))
        }

        val existingZone = zoneDao.getZoneById(zoneId)
            ?: return OperationResult.error(s.shared("service_error_zone_not_found"))

        if (token.isCancelled) return OperationResult.cancelled()

        // Parse tool_groups if provided (validation already done by ActionValidator/ValidationHelper)
        val toolGroupsJson = if (params.has("tool_groups")) {
            val toolGroupsValue = params.opt("tool_groups")
            // Allow explicit null to clear tool_groups
            when {
                toolGroupsValue == null || toolGroupsValue == JSONObject.NULL -> null
                toolGroupsValue is JSONArray -> toolGroupsValue.toString()
                toolGroupsValue is String -> toolGroupsValue
                else -> existingZone.tool_groups
            }
        } else {
            existingZone.tool_groups // Keep existing value if not provided
        }

        // Parse group if provided (zone group assignment for MainScreen organization)
        val group = if (params.has("group")) {
            val groupValue = params.opt("group")
            when {
                groupValue == null || groupValue == JSONObject.NULL -> null
                groupValue is String && groupValue.isNotBlank() -> groupValue
                else -> existingZone.group
            }
        } else {
            existingZone.group // Keep existing value if not provided
        }

        com.assistant.core.utils.LogManager.service("ZoneService.handleUpdate - params has group: ${params.has("group")}, group value: '$group', existing group: '${existingZone.group}'", "DEBUG")

        val updatedZone = existingZone.copy(
            name = params.optString("name").takeIf { it.isNotBlank() } ?: existingZone.name,
            description = params.optString("description").takeIf { it.isNotBlank() } ?: existingZone.description,
            tool_groups = toolGroupsJson,
            group = group,
            updated_at = System.currentTimeMillis()
        )

        com.assistant.core.utils.LogManager.service("ZoneService.handleUpdate - Updated zone with group: '${updatedZone.group}'", "DEBUG")

        zoneDao.updateZone(updatedZone)

        // Notify UI of zones change
        DataChangeNotifier.notifyZonesChanged()

        return OperationResult.success(mapOf(
            "zone_id" to updatedZone.id,
            "updated_at" to updatedZone.updated_at
        ))
    }
    
    /**
     * Delete zone
     */
    private suspend fun handleDelete(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val zoneId = params.optString("zone_id")
        if (zoneId.isBlank()) {
            return OperationResult.error(s.shared("service_error_zone_id_required"))
        }
        
        // Verify zone exists
        val existingZone = zoneDao.getZoneById(zoneId)
            ?: return OperationResult.error(s.shared("service_error_zone_not_found"))
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        zoneDao.deleteZoneById(zoneId)

        // Notify UI of zones change
        DataChangeNotifier.notifyZonesChanged()

        return OperationResult.success(mapOf(
            "zone_id" to zoneId,
            "name" to existingZone.name, // Include name for verbalization
            "deleted_at" to System.currentTimeMillis()
        ))
    }
    
    /**
     * Get single zone
     */
    private suspend fun handleGet(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val zoneId = params.optString("zone_id")
        if (zoneId.isBlank()) {
            return OperationResult.error(s.shared("service_error_zone_id_required"))
        }
        
        val zone = zoneDao.getZoneById(zoneId)
            ?: return OperationResult.error(s.shared("service_error_zone_not_found"))

        val zoneMap = mutableMapOf<String, Any?>(
            "id" to zone.id,
            "name" to zone.name,
            "description" to zone.description,
            "order_index" to zone.order_index,
            "created_at" to zone.created_at,
            "updated_at" to zone.updated_at
        )

        // Add tool_groups if present
        if (zone.tool_groups != null) {
            zoneMap["tool_groups"] = zone.tool_groups
        }

        // Add group if present
        if (zone.group != null) {
            zoneMap["group"] = zone.group
        }

        return OperationResult.success(mapOf("zone" to zoneMap))
    }
    
    
    /**
     * List all zones
     */
    private suspend fun handleList(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val zones = zoneDao.getAllZones()
        if (token.isCancelled) return OperationResult.cancelled()

        val zoneData = zones.map { zone ->
            val zoneMap = mutableMapOf<String, Any?>(
                "id" to zone.id,
                "name" to zone.name,
                "description" to zone.description,
                "order_index" to zone.order_index,
                "created_at" to zone.created_at,
                "updated_at" to zone.updated_at
            )

            // Add tool_groups if present
            if (zone.tool_groups != null) {
                zoneMap["tool_groups"] = zone.tool_groups
            }

            // Add group if present
            if (zone.group != null) {
                zoneMap["group"] = zone.group
            }

            zoneMap
        }

        return OperationResult.success(mapOf(
            "zones" to zoneData,
            "count" to zoneData.size
        ))
    }

    /**
     * Generates human-readable description of zone action
     * Format: substantive form (e.g., "Création de la zone \"Santé\"")
     * Usage: (a) UI validation display, (b) SystemMessage feedback
     */
    override fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (operation) {
            "create" -> {
                val name = params.optString("name", s.shared("content_unnamed"))
                s.shared("action_verbalize_create_zone").format(name)
            }
            "update" -> {
                val zoneId = params.optString("zone_id")
                val zoneName = getZoneName(zoneId, context) ?: s.shared("content_unnamed")
                s.shared("action_verbalize_update_zone").format(zoneName)
            }
            "delete" -> {
                val zoneId = params.optString("zone_id")
                // Try to get name from params first (enriched by CommandExecutor after delete),
                // otherwise fallback to DB lookup (which will fail if already deleted)
                val zoneName = params.optString("name").takeIf { it.isNotBlank() }
                    ?: getZoneName(zoneId, context)
                    ?: s.shared("content_unnamed")
                s.shared("action_verbalize_delete_zone").format(zoneName)
            }
            else -> s.shared("action_verbalize_unknown")
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
