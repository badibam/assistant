package com.assistant.core.services

import android.content.Context
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.Zone
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.services.OperationResult
import org.json.JSONObject

/**
 * Zone Service - Core service for zone operations
 * Implements the standard service pattern with cancellation token
 */
class ZoneService(private val context: Context) {
    private val database by lazy { AppDatabase.getDatabase(context) }
    private val zoneDao by lazy { database.zoneDao() }
    
    /**
     * Execute zone operation with cancellation support
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
                "get" -> handleGet(params, token)
                "get_all" -> handleGetAll(params, token)
                "list" -> handleList(params, token)
                else -> OperationResult.error("Unknown zone operation: $operation")
            }
        } catch (e: Exception) {
            OperationResult.error("Zone operation failed: ${e.message}")
        }
    }
    
    /**
     * Create a new zone
     */
    private suspend fun handleCreate(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val name = params.optString("name")
        if (name.isBlank()) {
            return OperationResult.error("Zone name is required")
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
            return OperationResult.error("Zone ID is required")
        }
        
        val existingZone = zoneDao.getZoneById(zoneId)
            ?: return OperationResult.error("Zone not found")
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        val updatedZone = existingZone.copy(
            name = params.optString("name").takeIf { it.isNotBlank() } ?: existingZone.name,
            description = params.optString("description").takeIf { it.isNotBlank() } ?: existingZone.description,
            updated_at = System.currentTimeMillis()
        )
        
        zoneDao.updateZone(updatedZone)
        
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
            return OperationResult.error("Zone ID is required")
        }
        
        // Verify zone exists
        val existingZone = zoneDao.getZoneById(zoneId)
            ?: return OperationResult.error("Zone not found")
        
        if (token.isCancelled) return OperationResult.cancelled()
        
        zoneDao.deleteZoneById(zoneId)
        
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
            return OperationResult.error("Zone ID is required")
        }
        
        val zone = zoneDao.getZoneById(zoneId)
            ?: return OperationResult.error("Zone not found")
        
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
     * Get all zones
     */
    private suspend fun handleGetAll(params: JSONObject, token: CancellationToken): OperationResult {
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
}
