package app.treelune.core.services

import app.treelune.core.themes.IconColor
import android.content.Context
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.validation.SchemaValidator
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.commands.CommandStatus
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.LogManager
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.toFieldDefinitions
import app.treelune.core.fields.toFieldDefinition
import app.treelune.core.fields.toJsonArray
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldNameGenerator
import app.treelune.core.fields.FieldConfigValidator
import app.treelune.core.fields.ValidationException
import app.treelune.core.fields.migration.EntryMigration
import androidx.room.withTransaction
import app.treelune.core.scheduling.CoreScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import app.treelune.core.utils.JsonUtils
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.selection.ReferenceKind
import org.json.JSONObject
import app.treelune.core.icons.Icons
import app.treelune.core.grid.Grid
import app.treelune.core.grid.Groups
import app.treelune.core.grid.ToolPositions

/**
 * ToolInstance Service - Core service for tool instance operations
 * Implements the standard service pattern with cancellation token
 */
class ToolInstanceService(private val context: Context) : ExecutableService {
    private val database by lazy { AppDatabase.getDatabase(context) }
    private val toolInstanceDao by lazy { database.toolInstanceDao() }
    private val zoneDao by lazy { database.zoneDao() }
    private val s = Strings.`for`(context = context)

    // Detached scope for the post-CRUD tick, which must not hold up the caller
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * Execute tool instance operation with cancellation support
     */
    override suspend fun execute(
        operation: String,
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        val result = try {
            when (operation) {
                "create" -> handleCreate(params, token)
                "update" -> handleUpdate(params, token)
                "delete" -> handleDelete(params, token)
                "duplicate" -> handleDuplicate(params, token)
                "list" -> handleGetByZone(params, token)  // zones/{id}/tools pattern
                "list_all" -> handleListAll(params, token) // All tool instances across zones
                "get" -> handleGetById(params, token)      // tools/{id} pattern
                "waiting" -> handleWaiting(params, token)
                "running" -> handleRunning(params)
                "place" -> handlePlace(params)
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        } catch (e: Exception) {
            OperationResult.error(s.shared("service_error_tool_instance_service").format(e.message ?: ""))
        }

        // An active tooltype keeps its config and its scheduled work in step: the config IS the
        // definition, so changing it changes what is owed. Without this, editing a recurrence
        // leaves the screen empty until the next heartbeat, with no way to tell a slow tick from
        // something broken. Mirrors what AutomationService does for automations.
        if (result.success && operation in listOf("create", "update", "delete", "duplicate")) {
            LogManager.service("ToolInstanceService: Triggering scheduler tick after $operation", "DEBUG")
            scope.launch {
                try {
                    CoreScheduler.tick()
                } catch (e: Exception) {
                    LogManager.service("ToolInstanceService: Error calling tick(): ${e.message}", "ERROR", e)
                }
            }
        }

        return result
    }
    
    /**
     * Create a new tool instance
     */
    private suspend fun handleCreate(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val zoneId = params.optString("zone_id")
        val toolType = params.optString("tooltype")
        // config travels as an object; it becomes a string only on its way into the column.
        val configJson = (params.optJSONObject("config") ?: JSONObject()).toString()

        if (zoneId.isBlank() || toolType.isBlank()) {
            return OperationResult.error(s.shared("service_error_zone_id_tool_type_required"))
        }
        // The app's own demo gives its ids; every other caller gets one made here
        val toolId = when (val given = app.treelune.core.coordinator.GivenId.read(params)) {
            app.treelune.core.coordinator.GivenId.Read.None -> java.util.UUID.randomUUID().toString()
            is app.treelune.core.coordinator.GivenId.Read.Accepted -> given.id
            is app.treelune.core.coordinator.GivenId.Read.Refused -> return OperationResult.error(s.shared("service_error_id_not_given").format(given.id))
        }

        if (token.isCancelled) return OperationResult.cancelled()

        // A custom field arriving without a technical name gets one here, the same way an
        // update assigns one. The prompt tells the AI to omit the name and let the app
        // generate it, and until this ran on create, a tool created that way kept fields the
        // data could not be keyed on -- the AI then had to guess a key, or send an empty one.
        // Unlike an update, a name that comes in is kept rather than refused: the guard over
        // there protects values already stored under the old key, and a tool being created
        // has none. The interface takes that route, assigning names in its editor.
        // A setting left out takes its declared default, as in the screen that creates a tool
        val namedConfigJson = ToolTypeManager.getToolType(toolType)?.let { type ->
            type.completeConfig(app.treelune.core.tools.ToolConfigSettings.withDefaults(type, JSONObject(assignMissingFieldNames(configJson)), context), null).toString()
        } ?: assignMissingFieldNames(configJson)

        iconColorRefusal(namedConfigJson)?.let { return OperationResult.error(it) }
        val iconCheck = checkIconName(withoutEmptyIconColor(namedConfigJson))
        val storedConfigJson = when (iconCheck) {
            is IconCheck.Refused -> return OperationResult.error(iconCheck.message)
            is IconCheck.Kept -> iconCheck.configJson
        }
        checkConfig(toolType, storedConfigJson)?.let { return OperationResult.error(it) }
        ToolTypeManager.getToolType(toolType)?.refuseConfig(JSONObject(storedConfigJson), context)?.let { return OperationResult.error(it) }

        val zone = zoneDao.getZoneById(zoneId)
            ?: return OperationResult.error(s.shared("service_error_zone_not_found"))
        // One of its zone's tool groups, or none
        Groups.refusal(Groups.held(JSONObject(storedConfigJson)), ToolPositions.zoneGroups(zone.tool_groups), s)?.let { return OperationResult.error(it) }

        if (token.isCancelled) return OperationResult.cancelled()

        val newToolInstance = database.withTransaction {
            arrive(ToolInstance(id = toolId, zone_id = zoneId, tooltype = toolType, config_json = storedConfigJson, grid_x = 0, grid_y = 0), zone.tool_groups)
                .also { toolInstanceDao.insertToolInstance(it) }
        }

        // Notify UI of tools change in this zone
        DataChangeNotifier.notifyToolsChanged(zoneId)

        return OperationResult.success(mapOf(
            "tool_instance_id" to newToolInstance.id,
            "zone_id" to newToolInstance.zone_id,
            "tooltype" to newToolInstance.tooltype
        ) + iconCheck.report())
    }
    
    /**
     * Update existing tool instance
     *
     * If custom fields are modified in the config, this will:
     * 1. Generate names for new fields
     * 2. Validate all field definitions
     * 3. Remove deleted fields from all tool_data entries
     * All operations are done atomically in a database transaction.
     */
    private suspend fun handleUpdate(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("tool_instance_id")
        var configJson = params.optJSONObject("config")?.toString() ?: ""
        val newZoneId = params.optString("zone_id").takeIf { it.isNotBlank() }

        if (toolInstanceId.isBlank()) {
            return OperationResult.error(s.shared("service_error_tool_instance_id_required"))
        }

        val existingTool = toolInstanceDao.getToolInstanceById(toolInstanceId)
            ?: return OperationResult.error(s.shared("service_error_tool_instance_not_found"))

        if (token.isCancelled) return OperationResult.cancelled()

        // Process custom fields if config is being updated
        if (configJson.isNotBlank()) {
            val processResult = processCustomFields(
                oldConfigJson = existingTool.config_json,
                newConfigJson = configJson,
                token = token
            )

            if (!processResult.success) {
                return processResult // Return error from custom fields processing
            }

            // Get the processed config with generated field names
            configJson = processResult.data?.get("processed_config") as? String ?: configJson
        }

        // Only a name that changes is checked: a config saved again with the icon it already
        // had stays savable, even if that name was stored before names were checked.
        val iconChanged = configJson.isNotBlank() &&
            JSONObject(configJson).optString("icon_name") != JSONObject(existingTool.config_json).optString("icon_name")
        val iconCheck = if (iconChanged) checkIconName(configJson) else IconCheck.Kept(configJson)
        when (iconCheck) {
            is IconCheck.Refused -> return OperationResult.error(iconCheck.message)
            is IconCheck.Kept -> configJson = iconCheck.configJson
        }
        if (configJson.isNotBlank()) {
            iconColorRefusal(configJson)?.let { return OperationResult.error(it) }
            configJson = withoutEmptyIconColor(configJson)
            ToolTypeManager.getToolType(existingTool.tooltype)?.let { type ->
                configJson = type.completeConfig(JSONObject(configJson), JSONObject(existingTool.config_json)).toString()
            }
            checkConfig(existingTool.tooltype, configJson)?.let { return OperationResult.error(it) }
            // A config sent without its display mode keeps the one the tool had
            configJson = withDisplayMode(configJson, JSONObject(existingTool.config_json).getString("display_mode"))
            ToolTypeManager.getToolType(existingTool.tooltype)?.refuseConfig(JSONObject(configJson), context)?.let { return OperationResult.error(it) }
        }

        // What the change does to the recorded entries: refused while it loses something the
        // caller has not agreed to lose, or leaves an entry without a value it now requires
        val migration = if (configJson.isNotBlank()) {
            val fill = when (val given = readFill(existingTool, configJson, params.optJSONObject("fill_values"))) {
                is Fill.Refused -> return OperationResult.error(given.message)
                is Fill.Values -> given.values
            }
            planMigration(existingTool, configJson, fill).also { plan ->
                refuseMigration(plan, params.optBoolean("confirm_migration", false))?.let { return it }
            }
        } else null

        // Store old zone_id for notification
        val oldZoneId = existingTool.zone_id

        val oldZone = zoneDao.getZoneById(existingTool.zone_id)
            ?: return OperationResult.error(s.shared("service_error_zone_not_found"))
        val newZone = if (newZoneId == null || newZoneId == oldZone.id) oldZone else zoneDao.getZoneById(newZoneId)
            ?: return OperationResult.error(s.shared("service_error_zone_not_found"))

        // Its group is one of its zone's tool groups. A tool changing zone without a config
        // leaves its group behind; a config given with the move names one of the new zone's.
        val storedConfig = JSONObject(configJson.takeIf { it.isNotBlank() } ?: existingTool.config_json)
        val emptiedGroup = Groups.held(storedConfig)?.takeIf { newZone.id != oldZone.id && configJson.isBlank() }
        if (emptiedGroup != null) storedConfig.remove("group")
        Groups.refusal(Groups.held(storedConfig), ToolPositions.zoneGroups(newZone.tool_groups), s)?.let { return OperationResult.error(it) }

        val changedTool = existingTool.copy(
            config_json = if (configJson.isBlank() && emptiedGroup == null) existingTool.config_json else storedConfig.toString(),
            zone_id = newZone.id,
            updated_at = System.currentTimeMillis()
        )

        if (token.isCancelled) return OperationResult.cancelled()

        // The config, the places it changes and what it does to the entries are one write:
        // none lands without the others
        val updatedTool = database.withTransaction {
            val placed = move(existingTool, changedTool, oldZone.tool_groups, newZone.tool_groups)
            toolInstanceDao.updateToolInstance(placed)
            if (migration != null) applyMigration(existingTool, migration)
            placed
        }
        if (migration != null && (migration.updated.isNotEmpty() || migration.deleted.isNotEmpty())) {
            DataChangeNotifier.notifyToolDataChanged(toolInstanceId, updatedTool.zone_id)
        }

        // Notify UI of tools change in affected zones
        if (newZoneId != null && newZoneId != oldZoneId) {
            // Tool moved to different zone - notify both old and new zones
            DataChangeNotifier.notifyToolsChanged(oldZoneId)
            DataChangeNotifier.notifyToolsChanged(newZoneId)
            LogManager.service("Tool $toolInstanceId moved from zone $oldZoneId to $newZoneId", "DEBUG")
        } else {
            // Only config changed - notify current zone
            DataChangeNotifier.notifyToolsChanged(updatedTool.zone_id)
        }

        return OperationResult.success(mapOf(
            "tool_instance_id" to updatedTool.id,
            "zone_id" to updatedTool.zone_id,
            "updated_at" to updatedTool.updated_at
        ) + iconCheck.report() + (migration?.let { report(it) } ?: emptyMap())
            + (emptiedGroup?.let { mapOf("group_emptied" to it) } ?: emptyMap()))
    }

    /** The values given for fields a config change makes required, or why they are refused. */
    private sealed interface Fill {
        data class Values(val values: Map<String, Any>) : Fill
        data class Refused(val message: String) : Fill
    }

    /**
     * The values [given] under "data" for the fields of "data" the config [newConfigJson] of
     * [tool] requires, each checked against its field: an entry that takes one must be valid.
     */
    private fun readFill(tool: ToolInstance, newConfigJson: String, given: JSONObject?): Fill {
        val data = given?.optJSONObject("data") ?: return Fill.Values(emptyMap())
        val toolType = ToolTypeManager.getToolType(tool.tooltype)
            ?: return Fill.Refused(s.shared("error_tooltype_not_found").format(tool.tooltype))
        val required = toolType.getEntryFields(JSONObject(newConfigJson), context).data.filter { it.required }.associateBy { it.definition.name }
        val values = mutableMapOf<String, Any>()
        JsonUtils.toMap(data).forEach { (name, given) ->
            val field = required[name]?.definition
                ?: return Fill.Refused(s.shared("service_error_migration_fill_unknown").format(name))
            // Rounded to its decimals, as any value written to an entry
            val value = app.treelune.core.fields.NumericPrecision.round(given, field) ?: return@forEach
            val schema = app.treelune.core.validation.Schema(
                id = "fill:$name", displayName = field.displayName, description = "",
                category = app.treelune.core.validation.SchemaCategory.TOOL_DATA,
                content = JSONObject().put("type", "object")
                    .put("properties", JSONObject().put(name, app.treelune.core.fields.FieldValueSchema.of(field)))
                    .toString()
            )
            val checked = SchemaValidator.validate(schema, mapOf(name to value), context)
            val refused = checked.errorMessage.takeIf { !checked.isValid }
                ?: app.treelune.core.fields.FieldValueValidator.validate(field, value, context).errorMessage
            if (refused != null) return Fill.Refused(refused)
            values[name] = value
        }
        return Fill.Values(values)
    }

    /** What the config [newConfigJson] would do to the entries of [tool], as its config stands now. */
    private suspend fun planMigration(tool: ToolInstance, newConfigJson: String, fill: Map<String, Any>): EntryMigration.Plan {
        val toolType = ToolTypeManager.getToolType(tool.tooltype)
            ?: throw IllegalStateException("Unknown tooltype ${tool.tooltype}")
        fun fieldsOf(config: JSONObject) = EntryMigration.Fields(
            data = toolType.getEntryFields(config, context).data,
            extra = config.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()
        )
        val old = fieldsOf(JSONObject(tool.config_json))
        val entries = database.toolDataDao().getByToolInstance(tool.id)
        // The tool instance of each entry the entries' references designate, for a field that
        // narrows the tools it takes
        val dataReferences = old.data.map { it.definition }.filter { it.type == FieldType.REFERENCE }
        val extraReferences = old.extra.filter { it.type == FieldType.REFERENCE }
        val referenced = entries.flatMap { entry ->
            val data = JsonUtils.toMap(entry.data)
            val extra = JsonUtils.toMap(entry.extra?.takeIf { it.isNotBlank() })
            dataReferences.mapNotNull { ReferenceTarget.referenceOf(data[it.name]) } +
                extraReferences.mapNotNull { ReferenceTarget.referenceOf(extra[it.name]) }
        }.filter { it.kind == ReferenceKind.ENTRY }.mapNotNull { it.id }.distinct()
        val instances = referenced.associateWith { database.toolDataDao().getById(it)?.toolInstanceId }
        val plan = EntryMigration.plan(
            old = old,
            new = fieldsOf(JSONObject(newConfigJson)),
            entries = entries,
            fill = fill,
            entryInstance = { instances[it] }
        )
        // What the tool type's own rules remove under the new config, deleted with the rest
        val removed = toolType.entriesRemovedByConfig(JSONObject(newConfigJson), entries, context)
            .filter { entry -> plan.deleted.none { it.id == entry.id } }
        if (removed.isEmpty()) return plan
        val removedIds = removed.map { it.id }.toSet()
        return plan.copy(updated = plan.updated.filter { it.id !in removedIds }, deleted = plan.deleted + removed)
    }

    /**
     * The refusal of [plan], or null when it can be applied: values it removes and entries it
     * deletes need [confirmed] (the screen asks the user, the AI says so explicitly), and an entry
     * left without the value of a field now required is never stored: the caller gives one in
     * "fill_values".
     *
     * The refusal carries the counts in "migration", for the screen to show them.
     */
    private fun refuseMigration(plan: EntryMigration.Plan, confirmed: Boolean): OperationResult? {
        if (plan.missing.isNotEmpty()) {
            val fields = plan.missing.entries.joinToString(", ") { (field, count) -> "data.$field ($count)" }
            return OperationResult.error(s.shared("service_error_migration_missing_values").format(fields), report(plan))
        }
        if (plan.losesData && !confirmed) {
            return OperationResult.error(
                s.shared("service_error_migration_unconfirmed").format(plan.removedValues, plan.deleted.size),
                report(plan)
            )
        }
        return null
    }

    /** What a migration does, under "migration" in a result. */
    private fun report(plan: EntryMigration.Plan): Map<String, Any> = mapOf("migration" to mapOf(
        "removed_values" to plan.removedValues,
        "deleted_entries" to plan.deleted.size,
        "filled_values" to plan.filledValues,
        "missing_values" to mapOf("data" to plan.missing)
    ))

    /** Writes [plan] to the entries of [tool], inside the caller's transaction. */
    private suspend fun applyMigration(tool: ToolInstance, plan: EntryMigration.Plan) {
        val dao = database.toolDataDao()
        plan.updated.forEach { dao.update(it.copy(updatedAt = System.currentTimeMillis())) }
        plan.deleted.forEach { dao.deleteById(it.id) }
        if (plan.deleted.isEmpty()) return
        // A rule spanning the entries (a manual order) settles again once some are gone
        val toolType = ToolTypeManager.getToolType(tool.tooltype) ?: return
        toolType.settleEntries({ dao.getByToolInstance(tool.id) }, null).forEach { dao.update(it) }
    }
    
    /**
     * Check a config exactly as it is about to be stored, against the schema generated from its
     * tool type's declaration (ToolConfigSettings). Every config write goes through here, whoever
     * makes it -- a screen or the AI -- so nothing the declaration refuses can be stored.
     *
     * @return The error to hand back, or null when the config is valid
     */
    private fun checkConfig(tooltype: String, configJson: String): String? {
        val toolType = ToolTypeManager.getToolType(tooltype)
            ?: return s.shared("error_tooltype_not_found").format(tooltype)
        val schema = app.treelune.core.tools.ToolConfigSettings.schema(toolType, "config:$tooltype", context)
        val result = SchemaValidator.validate(schema, JsonUtils.toMap(configJson), context)
        return if (result.isValid) null else result.errorMessage ?: s.shared("message_validation_error_simple")
    }

    /** [configJson] with [mode] as its display mode when it gives none: every config holds one. */
    private fun withDisplayMode(configJson: String, mode: String): String {
        val config = JSONObject(configJson)
        return if (config.has("display_mode")) configJson else config.put("display_mode", mode).toString()
    }

    /** [tool] placed at the bottom of its section's grid in its zone, whose groups are [toolGroups]. */
    private suspend fun arrive(tool: ToolInstance, toolGroups: String?): ToolInstance {
        val groups = ToolPositions.zoneGroups(toolGroups)
        val section = ToolPositions.section(tool, groups)
        val tiles = Grid.arrive(ToolPositions.tiles(toolInstanceDao.getToolInstancesByZone(tool.zone_id), groups, section), tool.id, ToolPositions.size(tool.config_json))
        val tile = tiles.last()
        return tool.copy(grid_x = tile.column, grid_y = tile.row)
    }

    /** [tool] leaving its section's grid: the tools after the rows it leaves empty move up. */
    private suspend fun leave(tool: ToolInstance, toolGroups: String?) {
        val groups = ToolPositions.zoneGroups(toolGroups)
        val tools = toolInstanceDao.getToolInstancesByZone(tool.zone_id)
        val tiles = Grid.leave(ToolPositions.tiles(tools, groups, ToolPositions.section(tool, groups)), tool.id)
        write(ToolPositions.moved(tools, tiles))
    }

    /**
     * [before] becoming [after], placed: a tool that changes zone or section leaves its grid and
     * arrives in the other one; one that changes size in its grid takes it where it is, the
     * tools it grows over moving down. The other tools that move are written here, [after] is
     * returned at its place for the caller to write.
     */
    private suspend fun move(before: ToolInstance, after: ToolInstance, oldGroups: String?, newGroups: String?): ToolInstance {
        val fromSection = ToolPositions.section(before, ToolPositions.zoneGroups(oldGroups))
        val toSection = ToolPositions.section(after, ToolPositions.zoneGroups(newGroups))
        if (before.zone_id != after.zone_id || fromSection != toSection) {
            leave(before, oldGroups)
            return arrive(after, newGroups)
        }
        val size = ToolPositions.size(after.config_json)
        if (size == ToolPositions.size(before.config_json)) return after

        val groups = ToolPositions.zoneGroups(newGroups)
        val tools = toolInstanceDao.getToolInstancesByZone(after.zone_id)
        val tiles = Grid.resize(ToolPositions.tiles(tools, groups, toSection), after.id, size)
        write(ToolPositions.moved(tools.filter { it.id != after.id }, tiles))
        val tile = tiles.single { it.id == after.id }
        return after.copy(grid_x = tile.column, grid_y = tile.row)
    }

    private suspend fun write(moved: List<ToolInstance>) {
        moved.forEach { toolInstanceDao.updatePosition(it.id, it.grid_x, it.grid_y) }
    }

    /**
     * Delete tool instance
     */
    private suspend fun handleDelete(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val toolInstanceId = params.optString("tool_instance_id")
        
        if (toolInstanceId.isBlank()) {
            return OperationResult.error(s.shared("service_error_tool_instance_id_required"))
        }
        
        val existingTool = toolInstanceDao.getToolInstanceById(toolInstanceId)
            ?: return OperationResult.error(s.shared("service_error_tool_instance_not_found"))

        if (token.isCancelled) return OperationResult.cancelled()

        // Extract name from config for verbalization
        val toolName = try {
            JSONObject(existingTool.config_json).optString("name", "")
        } catch (e: Exception) {
            ""
        }

        val zone = zoneDao.getZoneById(existingTool.zone_id)
            ?: return OperationResult.error(s.shared("service_error_zone_not_found"))

        // The tool and the rows its leaving closes are one write
        database.withTransaction {
            leave(existingTool, zone.tool_groups)
            toolInstanceDao.deleteToolInstanceById(toolInstanceId)
        }

        // Notify UI of tools change in this zone
        DataChangeNotifier.notifyToolsChanged(existingTool.zone_id)

        return OperationResult.success(mapOf(
            "tool_instance_id" to toolInstanceId,
            "name" to toolName, // Include name for verbalization
            "deleted_at" to System.currentTimeMillis()
        ))
    }

    /**
     * Duplicate an existing tool instance
     *
     * Creates a copy of the source tool, its name marked as a copy (copy_name)
     * in the specified target zone and group.
     */
    private suspend fun handleDuplicate(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceId = params.optString("tool_instance_id")
        val targetZoneId = params.optString("target_zone_id")
        val targetGroup = params.optString("target_group").takeIf { it.isNotBlank() }

        if (toolInstanceId.isBlank()) {
            return OperationResult.error(s.shared("service_error_tool_instance_id_required"))
        }

        if (targetZoneId.isBlank()) {
            return OperationResult.error(s.shared("service_error_zone_id_required"))
        }

        // Load source tool instance
        val sourceTool = toolInstanceDao.getToolInstanceById(toolInstanceId)
            ?: return OperationResult.error(s.shared("service_error_tool_instance_not_found"))

        if (token.isCancelled) return OperationResult.cancelled()

        // Parse and modify config
        val sourceConfig = try {
            JSONObject(sourceTool.config_json)
        } catch (e: Exception) {
            LogManager.service("Failed to parse source config: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("duplicate_error").format(e.message ?: "Invalid config"))
        }

        // Modify name to indicate it's a copy
        val originalName = sourceConfig.optString("name", "")
        val newName = if (originalName.isNotBlank()) {
            s.shared("copy_name").format(originalName)
        } else {
            s.shared("action_duplicate") // Fallback if name is empty
        }
        sourceConfig.put("name", newName)

        val targetZone = zoneDao.getZoneById(targetZoneId)
            ?: return OperationResult.error(s.shared("service_error_zone_not_found"))

        // The group given, one of the target zone's tool groups; none given, the copy keeps its
        // source's group in the same zone and has none in another
        when {
            targetGroup != null -> sourceConfig.put("group", targetGroup)
            targetZoneId != sourceTool.zone_id -> sourceConfig.remove("group")
        }
        Groups.refusal(Groups.held(sourceConfig), ToolPositions.zoneGroups(targetZone.tool_groups), s)?.let { return OperationResult.error(it) }

        if (token.isCancelled) return OperationResult.cancelled()

        // Create new tool instance in target zone, at the bottom of its section's grid
        val newToolInstance = database.withTransaction {
            arrive(ToolInstance(zone_id = targetZoneId, tooltype = sourceTool.tooltype, config_json = sourceConfig.toString(), grid_x = 0, grid_y = 0), targetZone.tool_groups)
                .also { toolInstanceDao.insertToolInstance(it) }
        }

        // Notify UI of tools change in target zone
        DataChangeNotifier.notifyToolsChanged(targetZoneId)

        LogManager.service("Duplicated tool $toolInstanceId to ${newToolInstance.id} in zone $targetZoneId", "INFO")

        return OperationResult.success(mapOf(
            "tool_instance_id" to newToolInstance.id,
            "source_tool_instance_id" to toolInstanceId,
            "zone_id" to newToolInstance.zone_id,
            "tooltype" to newToolInstance.tooltype,
            "name" to newName
        ))
    }

    /**
     * Get tool instances by zone
     */
    /**
     * What waits for the user (ToolTypeContract.getWaiting): how many entries of each tool pass
     * its tool type's conditions and the oldest of them, the tools of `zone_id` or all of them,
     * and the sum by zone. A
     * tool type that declares none is left out; a tool whose count cannot be read is logged and
     * left out, a tile never lying about a count it does not have.
     */
    private suspend fun handleWaiting(params: JSONObject, token: CancellationToken): OperationResult {
        val zoneId = params.optString("zone_id").takeIf { it.isNotEmpty() }
        val tools = if (zoneId != null) toolInstanceDao.getToolInstancesByZone(zoneId) else toolInstanceDao.getAllToolInstances()
        val coordinator = app.treelune.core.coordinator.Coordinator(context)
        val counts = mutableMapOf<String, Int>()
        val oldest = mutableMapOf<String, String>()
        for (tool in tools) {
            if (token.isCancelled) return OperationResult.cancelled()
            val conditions = ToolTypeManager.getToolType(tool.tooltype)?.getWaiting(JSONObject(tool.config_json)).orEmpty()
            if (conditions.isEmpty()) continue
            val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to tool.id, "filters" to conditions, "limit" to 1))
            val total = ((result.data?.get("pagination") as? Map<*, *>)?.get("total_entries") as? Number)?.toInt()
            if (!result.isSuccess || total == null) {
                LogManager.service("ToolInstanceService: waiting of ${tool.id} not read: ${result.error}", "ERROR")
                continue
            }
            counts[tool.id] = total
            // Entries come newest first: the last page of one is the oldest
            if (total > 0) {
                val last = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to tool.id, "filters" to conditions, "limit" to 1, "page" to total))
                ((last.data?.get("entries") as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("id")?.let { oldest[tool.id] = it as String }
            }
        }
        return OperationResult.success(mapOf(
            "tools" to counts,
            "oldest" to oldest,
            "zones" to tools.groupBy { it.zone_id }.mapValues { (_, inZone) -> inZone.sumOf { counts[it.id] ?: 0 } }
        ))
    }

    /**
     * The tools with a stopwatch running on one of their entries (a DURATION field running, for
     * any tool type), among those of `zone_id` or all of them, and the zones holding one.
     */
    private suspend fun handleRunning(params: JSONObject): OperationResult {
        val zoneId = params.optString("zone_id").takeIf { it.isNotEmpty() }
        val tools = if (zoneId != null) toolInstanceDao.getToolInstancesByZone(zoneId) else toolInstanceDao.getAllToolInstances()
        val running = database.toolDataDao().getToolsRunning().toSet()
        val shown = tools.filter { it.id in running }
        return OperationResult.success(mapOf(
            "tools" to shown.map { it.id },
            "zones" to shown.map { it.zone_id }.distinct()
        ))
    }

    /**
     * The places of the tools of one group section, moved in the zone's edit mode: `zone_id`,
     * `group` (absent for the ungrouped one) and `places`, `{tool_id: {"grid_x", "grid_y"}}` for
     * every tool of the section. Written in one go, or refused when they do not lay the section
     * out (Grid.isLaidOut). The screen alone moves tiles: the AI has no command for it.
     */
    private suspend fun handlePlace(params: JSONObject): OperationResult {
        val zone = zoneDao.getZoneById(params.optString("zone_id"))
            ?: return OperationResult.error(s.shared("service_error_zone_not_found"))
        val groups = ToolPositions.zoneGroups(zone.tool_groups)
        val section = ToolPositions.section(params.optString("group").takeIf { it.isNotEmpty() }, groups)
        val places = params.optJSONObject("places") ?: return OperationResult.error(s.shared("service_error_grid_places"))
        val tools = toolInstanceDao.getToolInstancesByZone(zone.id).filter { ToolPositions.section(it, groups) == section }
        if (places.keys().asSequence().toSet() != tools.map { it.id }.toSet()) return OperationResult.error(s.shared("service_error_grid_places"))
        val placed = tools.map { tool ->
            val place = places.getJSONObject(tool.id)
            tool.copy(grid_x = place.getInt("grid_x"), grid_y = place.getInt("grid_y"))
        }
        if (!Grid.isLaidOut(placed.map { ToolPositions.tile(it) })) return OperationResult.error(s.shared("service_error_grid_places"))
        database.withTransaction { write(ToolPositions.moved(tools, placed.map { ToolPositions.tile(it) })) }
        DataChangeNotifier.notifyToolsChanged(zone.id)
        return OperationResult.success(mapOf("zone_id" to zone.id, "count" to placed.size))
    }

    private suspend fun handleGetByZone(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val zoneId = params.optString("zone_id")
        if (zoneId.isBlank()) {
            return OperationResult.error(s.shared("service_error_zone_id_required"))
        }

        // Read include_config parameter (default false for minimal version)
        val includeConfig = params.optBoolean("include_config", false)
        val includePosition = params.optBoolean("include_position", false)

        val toolInstances = toolInstanceDao.getToolInstancesByZone(zoneId)
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceData = toolInstances.map { tool ->
            // Always extract name and description for display
            var name = ""
            var description = ""
            try {
                val configJson = JSONObject(tool.config_json)
                name = configJson.optString("name", "")
                description = configJson.optString("description", "")
            } catch (e: Exception) {
                // Keep empty strings
            }

            // Build result map - minimal version
            val resultMap = mutableMapOf<String, Any?>(
                "id" to tool.id,
                "zone_id" to tool.zone_id,
                "name" to name,
                "description" to description,
                "tooltype" to tool.tooltype
            )

            // The user's fields of its entries, which the AI writes beside the tool type's own and
            // would not know of otherwise: read as stored, a field of an unknown type included
            extraFieldsSummary(tool.config_json)?.let { resultMap["extra_fields"] = it }

            // Its place in the grid, for the screen: the AI neither sees nor changes places
            if (includePosition) {
                resultMap["grid_x"] = tool.grid_x
                resultMap["grid_y"] = tool.grid_y
            }

            // Conditionally add config_json and timestamps based on include_config parameter
            if (includeConfig) {
                resultMap["config"] = JsonUtils.toMap(tool.config_json)
                resultMap["created_at"] = tool.created_at
                resultMap["updated_at"] = tool.updated_at
            }

            resultMap
        }

        return OperationResult.success(mapOf(
            "tool_instances" to toolInstanceData,
            "count" to toolInstanceData.size
        ))
    }

    /**
     * List all tool instances across all zones
     */
    /** The name, label and type of each user's field of a tool's config [configJson]; null when it has none. */
    private fun extraFieldsSummary(configJson: String): List<Map<String, String>>? {
        val fields = JSONObject(configJson).optJSONArray("extra_fields")?.takeIf { it.length() > 0 } ?: return null
        return (0 until fields.length()).map { i ->
            val field = fields.getJSONObject(i)
            mapOf("name" to field.optString("name"), "display_name" to field.optString("display_name"), "type" to field.optString("type"))
        }
    }

    private suspend fun handleListAll(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        // Read include_config parameter (default false for minimal version)
        val includeConfig = params.optBoolean("include_config", false)
        val includePosition = params.optBoolean("include_position", false)

        val toolInstances = toolInstanceDao.getAllToolInstances()
        if (token.isCancelled) return OperationResult.cancelled()

        val toolInstanceData = toolInstances.map { tool ->
            // Always extract name and description for display
            var name = ""
            var description = ""
            try {
                val configJson = JSONObject(tool.config_json)
                name = configJson.optString("name", "")
                description = configJson.optString("description", "")
            } catch (e: Exception) {
                // Keep empty strings
            }

            // Build result map - minimal version
            val resultMap = mutableMapOf<String, Any?>(
                "id" to tool.id,
                "zone_id" to tool.zone_id,
                "name" to name,
                "description" to description,
                "tooltype" to tool.tooltype
            )

            // The user's fields of its entries, which the AI writes beside the tool type's own and
            // would not know of otherwise: read as stored, a field of an unknown type included
            extraFieldsSummary(tool.config_json)?.let { resultMap["extra_fields"] = it }

            // Its place in the grid, for the screen: the AI neither sees nor changes places
            if (includePosition) {
                resultMap["grid_x"] = tool.grid_x
                resultMap["grid_y"] = tool.grid_y
            }

            // Conditionally add config_json and timestamps based on include_config parameter
            if (includeConfig) {
                resultMap["config"] = JsonUtils.toMap(tool.config_json)
                resultMap["created_at"] = tool.created_at
                resultMap["updated_at"] = tool.updated_at
            }

            resultMap
        }

        return OperationResult.success(mapOf(
            "tool_instances" to toolInstanceData,
            "count" to toolInstanceData.size
        ))
    }

    /**
     * Get tool instance by ID
     */
    private suspend fun handleGetById(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        
        val toolInstanceId = params.optString("tool_instance_id")
        if (toolInstanceId.isBlank()) {
            return OperationResult.error(s.shared("service_error_tool_instance_id_required"))
        }
        
        val toolInstance = toolInstanceDao.getToolInstanceById(toolInstanceId)
            ?: return OperationResult.error(s.shared("service_error_tool_instance_not_found"))

        // Extract name from config JSON
        val name = try {
            JSONObject(toolInstance.config_json).optString("name", "")
        } catch (e: Exception) {
            ""
        }

        return OperationResult.success(mapOf(
            "tool_instance" to mapOf(
                "id" to toolInstance.id,
                "zone_id" to toolInstance.zone_id,
                "name" to name,
                "tooltype" to toolInstance.tooltype,
                "config" to JsonUtils.toMap(toolInstance.config_json),
                "created_at" to toolInstance.created_at,
                "updated_at" to toolInstance.updated_at
            )
        ))
    }

    /**
     * Generates human-readable description of tool instance action
     * Format: substantive form (e.g., "Création de l'outil \"Poids\" dans la zone \"Santé\"")
     * Usage: (a) UI validation display, (b) SystemMessage feedback
     */
    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (operation) {
            "create" -> {
                // For create, name is directly in params
                val toolName = params.optJSONObject("config")
                    ?.optString("name", s.shared("content_unnamed"))
                    ?: s.shared("content_unnamed")
                val zoneId = params.optString("zone_id")
                val zoneName = getZoneName(zoneId, context) ?: s.shared("content_unnamed")
                s.shared("action_verbalize_create_tool").format(toolName, zoneName)
            }
            "update" -> {
                val toolId = params.optString("tool_instance_id")
                val toolName = getToolName(toolId, context) ?: s.shared("content_unnamed")
                s.shared("action_verbalize_update_tool_config").format(toolName)
            }
            "delete" -> {
                val toolId = params.optString("tool_instance_id")
                // Try to get name from params first (enriched by CommandExecutor after delete),
                // otherwise fallback to DB lookup (which will fail if already deleted)
                val toolName = params.optString("name").takeIf { it.isNotBlank() }
                    ?: getToolName(toolId, context)
                    ?: s.shared("content_unnamed")
                s.shared("action_verbalize_delete_tool").format(toolName)
            }
            "waiting" -> s.shared("action_verbalize_tools_waiting")
            "running" -> s.shared("action_verbalize_tools_running")
            "place" -> s.shared("action_verbalize_tools_place")
            else -> s.shared("action_verbalize_unknown")
        }
    }

    /**
     * Helper to retrieve tool name by ID
     */
    private suspend fun getToolName(toolInstanceId: String, context: Context): String? {
        if (toolInstanceId.isBlank()) return null
        val coordinator = Coordinator(context)
        val result = coordinator.processUserAction("tools.get", mapOf(
            "tool_instance_id" to toolInstanceId
        ))
        return if (result.status == CommandStatus.SUCCESS) {
            val tool = result.data?.get("tool_instance") as? Map<*, *>
            tool?.get("name") as? String
        } else null
    }

    /**
     * Give a technical name to every field that arrived without one.
     *
     * Names already assigned during this pass count as taken, so two fields created together
     * under the same display name do not land on the same key.
     */
    private fun assignNames(fields: List<FieldDefinition>): List<FieldDefinition> {
        val takenNames = fields.map { it.name }.filter { it.isNotEmpty() }.toMutableList()
        return fields.map { field ->
            if (field.name.isEmpty()) {
                val generatedName = FieldNameGenerator.generateName(field.displayName, takenNames)
                takenNames.add(generatedName)
                field.copy(name = generatedName)
            } else {
                field
            }
        }
    }

    /** A config's icon colour is one of IconColor.NAMES, or none: any other name is refused with them. */
    private fun iconColorRefusal(configJson: String): String? {
        val config = JSONObject(configJson)
        val given = if (config.isNull(IconColor.KEY)) null else config.optString(IconColor.KEY).trim()
        return if (IconColor.accepts(given)) null
        else s.shared("service_error_icon_color_unknown").format(given, IconColor.NAMES.joinToString(", "))
    }

    /** The config without its icon colour when it is given as null or empty, which is how a command removes it. */
    private fun withoutEmptyIconColor(configJson: String): String {
        val config = JSONObject(configJson)
        if (!config.has(IconColor.KEY)) return configJson
        if (!config.isNull(IconColor.KEY) && config.optString(IconColor.KEY).isNotBlank()) return configJson
        config.remove(IconColor.KEY)
        return config.toString()
    }

    /** What storing a config's icon name comes to: kept, maybe under its current name, or refused. */
    private sealed interface IconCheck {
        /** Stored as [configJson]; [renamedFrom] is the former name it was given, if it was one. */
        data class Kept(val configJson: String, val renamedFrom: String? = null) : IconCheck
        data class Refused(val message: String) : IconCheck

        /** Said in the result when a former name was stored under the current one. */
        fun report(): Map<String, Any> {
            val kept = this as? Kept ?: return emptyMap()
            val from = kept.renamedFrom ?: return emptyMap()
            return mapOf("icon_renamed" to mapOf("from" to from, "to" to JSONObject(kept.configJson).getString("icon_name")))
        }
    }


    /**
     * An icon name is a Lucide name. A former one is stored under the name it became, and a
     * name that designates no icon is refused rather than stored and shown as two letters.
     */
    private fun checkIconName(configJson: String): IconCheck {
        val config = JSONObject(configJson)
        val given = config.optString("icon_name").takeIf { it.isNotBlank() } ?: return IconCheck.Kept(configJson)
        val stored = Icons.storedName(context, given)
            ?: return IconCheck.Refused(s.shared("service_error_icon_unknown").format(given))
        if (stored == given) return IconCheck.Kept(configJson)
        config.put("icon_name", stored)
        return IconCheck.Kept(config.toString(), renamedFrom = given)
    }

    /**
     * assignNames on the raw config of a tool being created, returning the config to store.
     *
     * A config with no custom_fields comes back untouched, so the caller can run this over
     * every creation without asking first.
     */
    private fun assignMissingFieldNames(configJson: String): String {
        val config = JSONObject(configJson)
        val fieldsArray = config.optJSONArray("extra_fields") ?: return configJson
        if (fieldsArray.length() == 0) return configJson

        val fields = (0 until fieldsArray.length()).map { fieldsArray.getJSONObject(it).toFieldDefinition() }
        config.put("extra_fields", assignNames(fields).toJsonArray())
        return config.toString()
    }

    /**
     * Processes custom fields changes during tool instance config update.
     *
     * Runs on every config update, whatever the caller:
     * 1. Refusing technical names this service never assigned (invented names, renames)
     * 2. Assigning a technical name to each field that arrives without one
     * 3. Validating all field definitions
     *
     * What the change does to the entries is EntryMigration's, once the whole config is checked.
     *
     * @param oldConfigJson Previous configuration JSON
     * @param newConfigJson New configuration JSON (with custom_fields possibly modified)
     * @param token Cancellation token
     * @return OperationResult with processed_config containing generated field names
     */
    private suspend fun processCustomFields(
        oldConfigJson: String,
        newConfigJson: String,
        token: CancellationToken
    ): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        try {
            val oldConfig = JSONObject(oldConfigJson)
            val newConfig = JSONObject(newConfigJson)

            // Extract custom_fields arrays
            val oldFieldsArray = oldConfig.optJSONArray("extra_fields")
            val newFieldsArray = newConfig.optJSONArray("extra_fields")

            // Without user's fields, there is nothing to name or check
            if (newFieldsArray == null || newFieldsArray.length() == 0) {
                return OperationResult.success(mapOf("processed_config" to newConfigJson))
            }

            // Parse field definitions
            val oldFields = if (oldFieldsArray != null) {
                oldFieldsArray.toFieldDefinitions()
            } else {
                emptyList<FieldDefinition>()
            }

            val newFieldsList = mutableListOf<FieldDefinition>()
            for (i in 0 until newFieldsArray.length()) {
                newFieldsList.add(newFieldsArray.getJSONObject(i).toFieldDefinition())
            }

            // Phase 0: A technical name is assigned here and nowhere else, so the only names a
            // caller may send are the ones this service handed out. A name matching no existing
            // field is either an invented one or a rename; the second silently destroys every
            // value stored under the old key, and nothing tells the two apart. Both are refused.
            val oldNames = oldFields.map { it.name }.toSet()
            val unknownNames = newFieldsList.map { it.name }.filter { it.isNotEmpty() && it !in oldNames }
            if (unknownNames.isNotEmpty()) {
                val message = s.shared("error_field_name_unknown").format(unknownNames.joinToString(", "))
                LogManager.service("Tool config sent unknown field name(s): ${unknownNames.joinToString(", ")}", "ERROR")
                return OperationResult.error(message)
            }

            // Phase 1: Assign a technical name to each new field (the ones sent without one).
            val processedFields = assignNames(newFieldsList)

            if (token.isCancelled) return OperationResult.cancelled()

            // Phase 2: Field definition validation (all fields must pass)
            val existingFieldsForValidation = processedFields.toMutableList()
            for ((index, field) in processedFields.withIndex()) {
                // For validation, exclude current field from existing list to avoid self-collision
                val otherFields = existingFieldsForValidation.filterIndexed { i, _ -> i != index }

                val validation = FieldConfigValidator.validate(field, otherFields, context)
                if (!validation.isValid) {
                    LogManager.service(
                        "Field validation failed for '${field.displayName}': ${validation.errorMessage}",
                        "ERROR"
                    )
                    return OperationResult.error(
                        "Validation custom field '${field.displayName}': ${validation.errorMessage}"
                    )
                }
            }

            // Phase 3: Build processed config with generated names
            val processedFieldsArray = processedFields.toJsonArray()
            newConfig.put("extra_fields", processedFieldsArray)

            return OperationResult.success(mapOf(
                "processed_config" to newConfig.toString()
            ))

        } catch (e: ValidationException) {
            LogManager.service("Custom fields validation error: ${e.message}", "ERROR", e)
            return OperationResult.error("Custom fields validation error: ${e.message}")
        } catch (e: Exception) {
            LogManager.service("Failed to process custom fields: ${e.message}", "ERROR", e)
            return OperationResult.error("Failed to process custom fields: ${e.message}")
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