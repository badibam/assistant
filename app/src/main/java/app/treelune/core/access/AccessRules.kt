package app.treelune.core.access

import android.content.Context
import app.treelune.core.database.AppDatabase

/** What an operation reaches, and how far it goes there. */
sealed class Reach {
    /** Nothing a mask holds: the lists of the zones, the tools and the variables, the schemas, the icons, the date */
    object Free : Reach()
    /** The app itself: a zone created, the home screen's settings */
    object App : Reach()
    data class Zone(val id: String, val level: AccessLevel) : Reach()
    data class Tool(val id: String, val level: AccessLevel) : Reach()
    /** A variable, by its id or by its name, held by its zone */
    data class Variable(val idOrName: String, val byName: Boolean, val level: AccessLevel) : Reach()
    /** An operation these rules do not know: refused under a mask, rather than let through */
    data class Unknown(val action: String) : Reach()
}

/** What the rules read to place what an operation names. */
interface AccessLookups {
    suspend fun toolZone(toolId: String): String?
    suspend fun variableZone(idOrName: String, byName: Boolean): String?
    suspend fun zoneName(zoneId: String): String?
    suspend fun toolName(toolId: String): String?
    /** Whether [resource] is a tool type, whose service runs the type's own operations on a tool. */
    fun isToolType(resource: String): Boolean
}

/**
 * Where each operation of the services goes, and the refusal of one beyond a mask (AccessMask).
 * The coordinator asks it for every command the app itself does not make, under an AccessScope.
 */
object AccessRules {

    private val TOOL_DATA_READS = setOf("get", "values", "get_single")
    private val TOOL_DATA_WRITES = setOf("create", "update", "delete", "batch_create", "batch_update", "batch_delete", "start_duration", "stop_duration", "delete_all")

    /** What [resource].[operation] reaches with [params]. */
    fun reach(resource: String, operation: String, params: Map<String, Any?>, isToolType: (String) -> Boolean): List<Reach> {
        fun text(key: String) = params[key] as? String
        val tool = text("tool_instance_id") ?: text("id")
        fun toolAt(level: AccessLevel) = tool?.let { Reach.Tool(it, level) } ?: Reach.Unknown("$resource.$operation")
        fun zoneAt(key: String, level: AccessLevel) = text(key)?.let { Reach.Zone(it, level) } ?: Reach.Unknown("$resource.$operation")
        fun variableAt(level: AccessLevel) = text("variable_id")?.let { Reach.Variable(it, false, level) } ?: Reach.Unknown("$resource.$operation")
        return when (resource) {
            "tool_data" -> when (operation) {
                in TOOL_DATA_READS -> listOf(toolAt(AccessLevel.READ))
                in TOOL_DATA_WRITES -> listOf(toolAt(AccessLevel.USE))
                else -> listOf(Reach.Unknown("$resource.$operation"))
            }
            "tools" -> when (operation) {
                "list", "list_all", "waiting" -> listOf(Reach.Free)
                "get" -> listOf(toolAt(AccessLevel.READ))
                "create" -> listOf(zoneAt("zone_id", AccessLevel.USE))
                // A tool moved goes into the zone it is moved to
                "update" -> listOfNotNull(toolAt(AccessLevel.FULL), text("zone_id")?.let { Reach.Zone(it, AccessLevel.USE) })
                "delete" -> listOf(toolAt(AccessLevel.FULL))
                else -> listOf(Reach.Unknown("$resource.$operation"))
            }
            "zones" -> when (operation) {
                "list" -> listOf(Reach.Free)
                "get" -> listOf(zoneAt("zone_id", AccessLevel.READ))
                "create" -> listOf(Reach.App)
                "update", "delete" -> listOf(zoneAt("zone_id", AccessLevel.FULL))
                else -> listOf(Reach.Unknown("$resource.$operation"))
            }
            "variables" -> when (operation) {
                "list", "list_all" -> listOf(Reach.Free)
                "get" -> listOf(variableAt(AccessLevel.READ))
                "create" -> listOf(zoneAt("zone_id", AccessLevel.USE))
                "update" -> listOfNotNull(variableAt(AccessLevel.USE), text("zone_id")?.let { Reach.Zone(it, AccessLevel.USE) })
                "delete" -> listOf(variableAt(AccessLevel.USE))
                else -> listOf(Reach.Unknown("$resource.$operation"))
            }
            "readings" -> listOf(text("variable")?.let { Reach.Variable(it, true, AccessLevel.READ) } ?: Reach.Unknown("$resource.$operation"))
            "app_config" -> if (operation == "get_current_datetime") listOf(Reach.Free) else listOf(Reach.App)
            "imports" -> when (operation) {
                "detect" -> listOf(toolAt(AccessLevel.READ))
                "apply" -> listOf(toolAt(AccessLevel.USE))
                else -> listOf(Reach.Unknown("$resource.$operation"))
            }
            "schemas", "icons" -> listOf(Reach.Free)
            "files" -> if (operation == "read") listOf(Reach.Free) else listOf(Reach.Unknown("$resource.$operation"))
            // A tool type's own operation writes the tool's entries
            else -> if (isToolType(resource)) listOf(toolAt(AccessLevel.USE)) else listOf(Reach.Unknown("$resource.$operation"))
        }
    }

    /**
     * Why [reaches] go beyond [mask], naming [holder] and the first thing out of reach; null when
     * they do not. Something these rules cannot place is out of reach.
     */
    suspend fun refusal(mask: AccessMask, holder: String, reaches: List<Reach>, lookups: AccessLookups, text: (String) -> String): String? {
        if (mask.open) return null
        fun level(level: AccessLevel) = text("access_level_${level.key}")
        fun refused(what: String, level: AccessLevel?) =
            if (level == null) text("access_refused_unknown").format(holder, what)
            else text("access_refused").format(holder, what, level(level))
        for (reach in reaches) {
            when (reach) {
                Reach.Free -> Unit
                Reach.App -> return refused(text("access_target_app"), null)
                is Reach.Unknown -> return refused(reach.action, null)
                is Reach.Zone -> if ((mask.zone(reach.id) ?: return refused(zone(reach.id, lookups, text), reach.level)) < reach.level)
                    return refused(zone(reach.id, lookups, text), reach.level)
                is Reach.Tool -> {
                    val zoneId = lookups.toolZone(reach.id) ?: return refused(tool(reach.id, lookups, text), reach.level)
                    if ((mask.tool(reach.id, zoneId) ?: return refused(tool(reach.id, lookups, text), reach.level)) < reach.level)
                        return refused(tool(reach.id, lookups, text), reach.level)
                }
                is Reach.Variable -> {
                    val zoneId = lookups.variableZone(reach.idOrName, reach.byName) ?: return refused(reach.idOrName, reach.level)
                    if ((mask.zone(zoneId) ?: return refused(zone(zoneId, lookups, text), reach.level)) < reach.level)
                        return refused(zone(zoneId, lookups, text), reach.level)
                }
            }
        }
        return null
    }

    private suspend fun zone(id: String, lookups: AccessLookups, text: (String) -> String) =
        text("access_target_zone").format(lookups.zoneName(id) ?: id)

    private suspend fun tool(id: String, lookups: AccessLookups, text: (String) -> String) =
        text("access_target_tool").format(lookups.toolName(id) ?: id)
}

/** The lookups read from the database. */
class DatabaseAccessLookups(context: Context) : AccessLookups {
    private val database = AppDatabase.getDatabase(context)

    override suspend fun toolZone(toolId: String) = database.toolInstanceDao().getToolInstanceById(toolId)?.zone_id

    override suspend fun variableZone(idOrName: String, byName: Boolean) =
        if (byName) database.variableDao().getAll().firstOrNull { it.name == idOrName }?.zoneId
        else database.variableDao().getById(idOrName)?.zoneId

    override suspend fun zoneName(zoneId: String) = database.zoneDao().getZoneById(zoneId)?.name

    override suspend fun toolName(toolId: String) =
        database.toolInstanceDao().getToolInstanceById(toolId)?.let { org.json.JSONObject(it.config_json).optString("name").ifEmpty { null } }

    override fun isToolType(resource: String) = app.treelune.core.tools.ToolTypeManager.getToolType(resource) != null
}
