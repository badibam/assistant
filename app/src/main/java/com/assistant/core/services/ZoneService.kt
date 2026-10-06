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
import com.assistant.core.grid.Grid
import com.assistant.core.grid.Groups
import com.assistant.core.grid.ZonePositions
import com.assistant.core.database.entities.Zone as ZoneEntity
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
                "place" -> handlePlace(params)
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
        // The app's own demo gives its ids; every other caller gets one made here
        val zoneId = when (val given = com.assistant.core.coordinator.GivenId.read(params)) {
            com.assistant.core.coordinator.GivenId.Read.None -> java.util.UUID.randomUUID().toString()
            is com.assistant.core.coordinator.GivenId.Read.Accepted -> given.id
            is com.assistant.core.coordinator.GivenId.Read.Refused -> return OperationResult.error(s.shared("service_error_id_not_given").format(given.id))
        }

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

        // One of the home screen's groups, or none
        val group = Groups.held(params)
        Groups.refusal(group, zoneGroups(), s)?.let { return OperationResult.error(it) }

        if (token.isCancelled) return OperationResult.cancelled()

        val placing = Zone(
            id = zoneId,
            name = name,
            description = description,
            icon_name = icon.name,
            display_mode = params.givenText("display_mode") ?: "LINE",
            grid_x = 0,
            grid_y = 0,
            tool_groups = toolGroupsJson,
            group = group
        )
        checkZone(placing)?.let { return OperationResult.error(it) }


        if (token.isCancelled) return OperationResult.cancelled()

        // At the bottom of its section's grid on the home screen
        val newZone = database.withTransaction {
            val groups = zoneGroups()
            val tile = Grid.arrive(ZonePositions.tiles(zoneDao.getAllZones(), groups, ZonePositions.section(group, groups)), placing.id, ZonePositions.size(placing)).last()
            placing.copy(grid_x = tile.column, grid_y = tile.row).also { zoneDao.insertZone(it) }
        }

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
            put("display_mode", zone.display_mode)
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

    /** The home screen's zone groups. */
    private suspend fun zoneGroups(): List<String> = AppConfigService(context).getZoneGroups()

    private suspend fun write(moved: List<ZoneEntity>) {
        moved.forEach { zoneDao.updatePosition(it.id, it.grid_x, it.grid_y) }
    }

    /**
     * [before] becoming [after], placed on the home screen: a zone that changes section leaves its
     * grid and arrives in the other one; one that changes mode takes its new size where it is, the
     * zones it grows over moving down. The other zones that move are written here, [after] is
     * returned at its place for the caller to write.
     */
    private suspend fun move(before: ZoneEntity, after: ZoneEntity): ZoneEntity {
        val groups = zoneGroups()
        val zones = zoneDao.getAllZones()
        val from = ZonePositions.section(before.group, groups)
        val to = ZonePositions.section(after.group, groups)
        if (from != to) {
            write(ZonePositions.moved(zones, Grid.leave(ZonePositions.tiles(zones, groups, from), before.id)))
            val tile = Grid.arrive(ZonePositions.tiles(zoneDao.getAllZones().filter { it.id != after.id }, groups, to), after.id, ZonePositions.size(after)).last()
            return after.copy(grid_x = tile.column, grid_y = tile.row)
        }
        val size = ZonePositions.size(after)
        if (size == ZonePositions.size(before)) return after
        val tiles = Grid.resize(ZonePositions.tiles(zones, groups, to), after.id, size)
        write(ZonePositions.moved(zones.filter { it.id != after.id }, tiles))
        val tile = tiles.single { it.id == after.id }
        return after.copy(grid_x = tile.column, grid_y = tile.row)
    }

    /**
     * The places of the zones of one zone group section, moved in the home screen's edit mode:
     * `group` (absent for the ungrouped one) and `places`, `{zone_id: {"grid_x", "grid_y"}}` for
     * every zone of the section. Written in one go, or refused when they do not lay the section
     * out (Grid.isLaidOut). The screen alone moves tiles: the AI has no command for it.
     */
    private suspend fun handlePlace(params: JSONObject): OperationResult {
        val groups = zoneGroups()
        val section = ZonePositions.section(params.optString("group").takeIf { it.isNotEmpty() }, groups)
        val places = params.optJSONObject("places") ?: return OperationResult.error(s.shared("service_error_grid_places"))
        val zones = zoneDao.getAllZones().filter { ZonePositions.section(it.group, groups) == section }
        if (places.keys().asSequence().toSet() != zones.map { it.id }.toSet()) return OperationResult.error(s.shared("service_error_grid_places"))
        val placed = zones.map { zone ->
            val place = places.getJSONObject(zone.id)
            ZonePositions.tile(zone).copy(column = place.getInt("grid_x"), row = place.getInt("grid_y"))
        }
        if (!Grid.isLaidOut(placed)) return OperationResult.error(s.shared("service_error_grid_places"))
        database.withTransaction { write(ZonePositions.moved(zones, placed)) }
        DataChangeNotifier.notifyZonesChanged()
        return OperationResult.success(mapOf("count" to placed.size))
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

        // One of the home screen's groups, or none: null or empty removes it
        val group = if (params.has("group")) Groups.held(params) else existingZone.group
        Groups.refusal(group, zoneGroups(), s)?.let { return OperationResult.error(it) }

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

        val displayMode = if (params.has("display_mode")) params.givenText("display_mode") ?: existingZone.display_mode else existingZone.display_mode
        val updatedZone = existingZone.copy(
            name = name,
            description = description,
            icon_name = icon.name,
            display_mode = displayMode,
            tool_groups = toolGroupsJson,
            group = group,
            updated_at = System.currentTimeMillis()
        )

        LogManager.service("ZoneService.handleUpdate - Updated zone with group: '${updatedZone.group}'", "DEBUG")

        checkZone(updatedZone)?.let { return OperationResult.error(it) }

        // The zone's tool groups are held by its tools, automations and variables: a group
        // renamed is renamed in them, a group removed while one holds it is refused
        val toolDao = database.toolInstanceDao()
        val automationDao = database.aiDao()
        val variableDao = database.variableDao()
        val change = Groups.Change(ToolPositions.zoneGroups(existingZone.tool_groups), ToolPositions.zoneGroups(updatedZone.tool_groups), Groups.renames(params, "tool_groups"))
        val tools = toolDao.getToolInstancesByZone(zoneId)
        val automations = automationDao.getAutomationsByZone(zoneId)
        val variables = variableDao.getByZone(zoneId)
        Groups.changeRefusal(change,
            tools.map { JSONObject(it.config_json).optString("name") to Groups.held(JSONObject(it.config_json)) } +
                automations.map { it.name to it.group } + variables.map { it.name to it.group },
            s
        )?.let { return OperationResult.error(it) }

        // A tool whose group the zone gains or loses changes section, and so grid: the zone,
        // the names it changes and the places it changes are one write
        val regrouped = database.withTransaction {
            val renamedTools = tools.map { tool ->
                val config = JSONObject(tool.config_json)
                val group = change.held(Groups.held(config))
                if (group == Groups.held(config)) tool
                else tool.copy(config_json = config.put("group", group).toString()).also { toolDao.updateToolInstance(it) }
            }
            automations.filter { change.held(it.group) != it.group }.forEach { automationDao.updateAutomation(it.copy(group = change.held(it.group))) }
            variables.filter { change.held(it.group) != it.group }.forEach { variableDao.update(it.copy(group = change.held(it.group))) }
            val moved = if (change.beforeRenamed == change.after) emptyList() else ToolPositions.regroup(renamedTools, change.beforeRenamed, change.after)
            moved.forEach { toolDao.updatePosition(it.id, it.grid_x, it.grid_y) }
            zoneDao.updateZone(move(existingZone, updatedZone))
            moved
        }

        // Notify UI of zones change
        DataChangeNotifier.notifyZonesChanged()
        if (regrouped.isNotEmpty() || change.renames.isNotEmpty()) DataChangeNotifier.notifyToolsChanged(zoneId)
        if (change.renames.isNotEmpty()) DataChangeNotifier.notifyVariablesChanged()

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
        
        // The zone and the rows its leaving closes on the home screen are one write
        database.withTransaction {
            val groups = zoneGroups()
            val zones = zoneDao.getAllZones()
            write(ZonePositions.moved(zones, Grid.leave(ZonePositions.tiles(zones, groups, ZonePositions.section(existingZone.group, groups)), zoneId)))
            zoneDao.deleteZoneById(zoneId)
        }

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
            "display_mode" to zone.display_mode,
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
                "display_mode" to zone.display_mode,
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

            // Its place on the home screen, for the screen: the AI neither sees nor changes places
            if (params.optBoolean("include_position", false)) {
                zoneMap["grid_x"] = zone.grid_x
                zoneMap["grid_y"] = zone.grid_y
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
            "place" -> s.shared("action_verbalize_zones_place")
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
