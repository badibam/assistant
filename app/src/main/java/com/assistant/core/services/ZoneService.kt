package com.assistant.core.services

import com.assistant.core.utils.JsonUtils
import android.content.Context
import com.assistant.core.icons.Icons
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.Zone
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.utils.DataChangeNotifier
import org.json.JSONObject
import org.json.JSONArray
import com.assistant.core.utils.LogManager
import com.assistant.core.grid.ToolPositions
import androidx.room.withTransaction

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

        val name = params.givenText("name")
            ?: return OperationResult.error(s.shared("service_error_zone_name_required"))

        val description = params.givenText("description")

        val icon = storedIcon(params.givenText("icon_name"))
        if (icon is StoredIcon.Refused) return OperationResult.error(icon.message)
        icon as StoredIcon.Kept

        // Parse tool_groups if provided (checked with the rest, checkZone)
        val toolGroupsJson = if (params.has("tool_groups")) {
            params.optJSONArray("tool_groups")?.toString()
        } else {
            null
        }

        // Parse group if provided (zone group assignment for MainScreen organization)
        val group = params.optString("group").takeIf { it.isNotBlank() }

        LogManager.service("ZoneService.handleCreate - params has group: ${params.has("group")}, group value: '$group'", "DEBUG")

        // Get current max order_index for proper ordering
        if (token.isCancelled) return OperationResult.cancelled()

        // For now, use simple ordering - could be enhanced later
        val orderIndex = System.currentTimeMillis().toInt() % 1000

        val newZone = Zone(
            name = name,
            description = description,
            icon_name = icon.name,
            order_index = orderIndex,
            tool_groups = toolGroupsJson,
            group = group
        )

        LogManager.service("ZoneService.handleCreate - Created zone with group: '${newZone.group}'", "DEBUG")

        checkZone(newZone)?.let { return OperationResult.error(it) }
        if (token.isCancelled) return OperationResult.cancelled()

        zoneDao.insertZone(newZone)

        // Notify UI of zones change
        DataChangeNotifier.notifyZonesChanged()

        return OperationResult.success(mapOf(
            "zone_id" to newZone.id,
            "name" to newZone.name,
            "created_at" to newZone.created_at
        ) + icon.report())
    }
    
    /** What storing a zone's icon name comes to: kept, maybe under its current name, or refused. */
    private sealed interface StoredIcon {
        /** Stored as [name]; [renamedFrom] is the former name it was given, if it was one. */
        data class Kept(val name: String?, val renamedFrom: String? = null) : StoredIcon
        data class Refused(val message: String) : StoredIcon

        /** Said in the result when a former name was stored under the current one. */
        fun report(): Map<String, Any> {
            val kept = this as? Kept ?: return emptyMap()
            val from = kept.renamedFrom ?: return emptyMap()
            return mapOf("icon_renamed" to mapOf("from" to from, "to" to kept.name))
        }
    }

    /**
     * An icon name is a Lucide name. A former one is stored under the name it became, and a
     * name that designates no icon is refused rather than stored and shown as two letters.
     */
    private fun storedIcon(given: String?): StoredIcon {
        if (given == null) return StoredIcon.Kept(null)
        val stored = Icons.storedName(context, given)
            ?: return StoredIcon.Refused(s.shared("service_error_icon_unknown").format(given))
        return StoredIcon.Kept(stored, renamedFrom = given.takeIf { it != stored })
    }

    /**
     * Check a zone's settings exactly as they are about to be stored, against the schema
     * generated from their declaration (ZoneSettings). Every write goes through here, whoever
     * makes it -- the zone screen or the AI.
     *
     * @return The error to hand back, or null when the zone is valid
     */
    private fun checkZone(zone: Zone): String? {
        val settings = buildMap<String, Any?> {
            put("name", zone.name)
            zone.description?.let { put("description", it) }
            zone.icon_name?.let { put("icon_name", it) }
            zone.group?.let { put("group", it) }
            zone.tool_groups?.let { put("tool_groups", JsonUtils.toList(it)) }
        }
        val result = com.assistant.core.validation.SchemaValidator.validate(com.assistant.core.schemas.ZoneSettings.schema(context), settings, context)
        return if (result.isValid) null else result.errorMessage ?: s.shared("message_validation_error_simple")
    }

    /**
     * A text parameter as given: null when it is JSON null or blank, which is how a command
     * empties a field. optString alone would read JSON null as the text "null".
     */
    private fun JSONObject.givenText(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

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

        // Parse tool_groups if provided (checked with the rest, checkZone)
        val toolGroupsJson = if (params.has("tool_groups")) {
            // Allow explicit null to clear tool_groups
            val toolGroupsValue = params.opt("tool_groups")
            when {
                toolGroupsValue == null || toolGroupsValue == JSONObject.NULL -> null
                toolGroupsValue is JSONArray -> toolGroupsValue.toString()
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

        LogManager.service("ZoneService.handleUpdate - params has group: ${params.has("group")}, group value: '$group', existing group: '${existingZone.group}'", "DEBUG")

        // A partial update: a field left out keeps its value, a field given replaces it, and a
        // field given as null or empty is emptied -- except the name, which a zone must have.
        val name = if (params.has("name")) {
            params.givenText("name") ?: return OperationResult.error(s.shared("service_error_zone_name_required"))
        } else existingZone.name
        val description = if (params.has("description")) params.givenText("description") else existingZone.description

        // Only an icon that changes is checked, like a tool's: saving a zone again with the
        // icon it has is always allowed.
        val icon = if (params.has("icon_name")) {
            val givenIcon = params.givenText("icon_name")
            if (givenIcon == existingZone.icon_name) StoredIcon.Kept(givenIcon) else storedIcon(givenIcon)
        } else StoredIcon.Kept(existingZone.icon_name)
        if (icon is StoredIcon.Refused) return OperationResult.error(icon.message)
        icon as StoredIcon.Kept

        val updatedZone = existingZone.copy(
            name = name,
            description = description,
            icon_name = icon.name,
            tool_groups = toolGroupsJson,
            group = group,
            updated_at = System.currentTimeMillis()
        )

        LogManager.service("ZoneService.handleUpdate - Updated zone with group: '${updatedZone.group}'", "DEBUG")

        checkZone(updatedZone)?.let { return OperationResult.error(it) }

        // A tool whose group the zone gains or loses changes section, and so grid: the zone
        // and the places it changes are one write
        val toolDao = database.toolInstanceDao()
        val regrouped = database.withTransaction {
            val moved = if (updatedZone.tool_groups == existingZone.tool_groups) emptyList() else ToolPositions.regroup(
                toolDao.getToolInstancesByZone(zoneId),
                ToolPositions.zoneGroups(existingZone.tool_groups),
                ToolPositions.zoneGroups(updatedZone.tool_groups)
            )
            moved.forEach { toolDao.updatePosition(it.id, it.grid_x, it.grid_y) }
            zoneDao.updateZone(updatedZone)
            moved
        }

        // Notify UI of zones change
        DataChangeNotifier.notifyZonesChanged()
        if (regrouped.isNotEmpty()) DataChangeNotifier.notifyToolsChanged(zoneId)

        return OperationResult.success(mapOf(
            "zone_id" to updatedZone.id,
            "updated_at" to updatedZone.updated_at
        ) + icon.report())
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
            "icon_name" to zone.icon_name,
            "order_index" to zone.order_index,
            "created_at" to zone.created_at,
            "updated_at" to zone.updated_at
        )

        // Add tool_groups if present
        if (zone.tool_groups != null) {
            zoneMap["tool_groups"] = JsonUtils.toList(zone.tool_groups)
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
                "icon_name" to zone.icon_name,
                "order_index" to zone.order_index,
                "created_at" to zone.created_at,
                "updated_at" to zone.updated_at
            )

            // Add tool_groups if present
            if (zone.tool_groups != null) {
                zoneMap["tool_groups"] = JsonUtils.toList(zone.tool_groups)
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
    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
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
     */
    private suspend fun getZoneName(zoneId: String, context: Context): String? {
        if (zoneId.isBlank()) return null
        val coordinator = Coordinator(context)
        val result = coordinator.processUserAction("zones.get", mapOf("zone_id" to zoneId))
        return if (result.status == CommandStatus.SUCCESS) {
            val zone = result.data?.get("zone") as? Map<*, *>
            zone?.get("name") as? String
        } else null
    }
}
