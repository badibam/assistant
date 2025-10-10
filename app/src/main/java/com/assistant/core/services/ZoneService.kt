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
        
        // Get current max order_index for proper ordering
        if (token.isCancelled) return OperationResult.cancelled()
        
        // For now, use simple ordering - could be enhanced later
        val orderIndex = System.currentTimeMillis().toInt() % 1000
        
        val newZone = Zone(
            name = name,
            description = description,
            order_index = orderIndex
        )
        
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
        
        val updatedZone = existingZone.copy(
            name = params.optString("name").takeIf { it.isNotBlank() } ?: existingZone.name,
            description = params.optString("description").takeIf { it.isNotBlank() } ?: existingZone.description,
            updated_at = System.currentTimeMillis()
        )
        
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
        
        return OperationResult.success(mapOf(
            "zone" to mapOf(
                "id" to zone.id,
                "name" to zone.name,
                "description" to zone.description,
                "order_index" to zone.order_index,
                "created_at" to zone.created_at,
                "updated_at" to zone.updated_at
            )
        ))
    }
    
    
    /**
     * List all zones
     */
    private suspend fun handleList(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val zones = zoneDao.getAllZones()
        if (token.isCancelled) return OperationResult.cancelled()
        
        val zoneData = zones.map { zone ->
            mapOf(
                "id" to zone.id,
                "name" to zone.name,
                "description" to zone.description,
                "order_index" to zone.order_index,
                "created_at" to zone.created_at,
                "updated_at" to zone.updated_at
            )
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
                val zoneName = getZoneName(zoneId, context) ?: s.shared("content_unnamed")
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
