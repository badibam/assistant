package com.assistant.core.demo

import android.content.Context
import androidx.room.withTransaction
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.Source
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.AppSettingCategories
import com.assistant.core.grid.Grid
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.ui.DisplayMode
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.JsonUtils
import com.assistant.core.utils.LogManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * The demo (docs/design/demo.md), as the resource `demo`:
 * - install: removes the demo there is, then builds the one the app ships through the services,
 *   as the app itself (Source.SYSTEM), which alone may give the demo's ids; its zone group joins
 *   the home screen's groups when it is not there yet, and never leaves them
 * - remove: removes the demo, what lives in it included
 *
 * Built through the services, the demo is what the app produces and is checked as anything else
 * written. A step refused stops the install and removes what it built: the demo is whole or
 * absent, and the refusal says why.
 */
class DemoService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)
    private val database = AppDatabase.getDatabase(context)
    private val coordinator = Coordinator(context)

    /** A step of the install refused, with the service's reason. */
    private class Refused(message: String) : Exception(message)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        return when (operation) {
            "install" -> install()
            "remove" -> remove()
            else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        }
    }

    private suspend fun install(): OperationResult {
        val content = try {
            DemoContent.read(asset("demo/structure.json"), asset("demo/${s.shared("demo_texts_file")}"))
        } catch (e: Exception) {
            LogManager.service("Demo not read: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("demo_error_unreadable").format(e.message ?: ""))
        }
        database.withTransaction { removeAll() }
        return try {
            addZoneGroup(content.group)
            build(content)
            LogManager.service("Demo installed: ${content.zones.size} zones, ${content.tools.size} tools, ${content.variables.size} variables", "INFO")
            OperationResult.success(mapOf("zones" to content.zones.size, "tools" to content.tools.size, "variables" to content.variables.size))
        } catch (e: Refused) {
            database.withTransaction { removeAll() }
            DataChangeNotifier.notifyZonesChanged()
            LogManager.service("Demo not installed: ${e.message}", "ERROR")
            OperationResult.error(s.shared("demo_error_refused").format(e.message ?: ""))
        }
    }

    /**
     * The zones, placed; the tools that read no variable; the variables, which read those tools;
     * the tools that read a variable (a goal, a chart); then every tool placed, section by section.
     */
    private suspend fun build(content: DemoContent) {
        content.zones.forEach { run("zones.create", DemoContent.paramsOf(it)) }
        placeZones(content)
        val (readingVariables, others) = content.tools.partition { DemoContent.readsVariable(it) }
        others.forEach { run("tools.create", DemoContent.paramsOf(it)) }
        content.variables.forEach { run("variables.create", it) }
        readingVariables.forEach { run("tools.create", DemoContent.paramsOf(it)) }
        content.tools.groupBy { it.getString("zone_id") to it.getJSONObject("config").optString("group").takeIf { g -> g.isNotEmpty() } }
            .forEach { (section, tools) ->
                run("tools.place", JSONObject()
                    .put("zone_id", section.first)
                    .apply { section.second?.let { put("group", it) } }
                    .put("places", places(tools)))
            }
    }

    /**
     * The demo's zones at their places in their group; a zone of the user's own standing in the
     * same group goes below them, in the order they had.
     */
    private suspend fun placeZones(content: DemoContent) {
        val listed = run("zones.list", JSONObject().put("include_position", true))
        @Suppress("UNCHECKED_CAST")
        val others = (listed["zones"] as List<Map<String, Any?>>)
            .filter { it["group"] == content.group && !(it["id"] as String).startsWith(DemoContent.PREFIX) }
            .sortedWith(compareBy({ (it["grid_y"] as Number).toInt() }, { (it["grid_x"] as Number).toInt() }))
        var tiles = content.zones.map { zone ->
            val size = Grid.size(DisplayMode.valueOf(zone.getString("display_mode")))
            Grid.Tile(zone.getString("id"), zone.getInt("grid_x"), zone.getInt("grid_y"), size.width, size.height)
        }
        others.forEach { zone ->
            tiles = Grid.arrive(tiles, zone["id"] as String, Grid.size(DisplayMode.valueOf(zone["display_mode"] as String)))
        }
        val places = JSONObject()
        tiles.forEach { places.put(it.id, JSONObject().put("grid_x", it.column).put("grid_y", it.row)) }
        run("zones.place", JSONObject().put("group", content.group).put("places", places))
    }

    private fun places(items: List<JSONObject>): JSONObject = JSONObject().also { places ->
        items.forEach { places.put(it.getString("id"), JSONObject().put("grid_x", it.getInt("grid_x")).put("grid_y", it.getInt("grid_y"))) }
    }

    /** Runs [action] as the app itself, its data on success; a refusal stops the install, naming the step. */
    private suspend fun run(action: String, params: JSONObject): Map<String, Any?> {
        val result = coordinator.process(Source.SYSTEM, action, JsonUtils.toMap(params))
        if (!result.isSuccess) throw Refused("$action ${params.optString("id")}: ${result.error}")
        return result.data ?: emptyMap()
    }

    private suspend fun remove(): OperationResult {
        database.withTransaction { removeAll() }
        DataChangeNotifier.notifyZonesChanged()
        LogManager.service("Demo removed", "INFO")
        return OperationResult.success()
    }

    private suspend fun removeAll() {
        val dao = database.demoDao()
        dao.deleteEntries()
        dao.deleteTools()
        dao.deleteVariables()
        dao.deleteSessions()
        dao.deleteAutomations()
        dao.deleteZones()
    }

    /**
     * Adds [group] to the home screen's zone groups through the settings service, which places
     * the zones again; nothing when it is there.
     */
    private suspend fun addZoneGroup(group: String) {
        val read = run("app_config.get", JSONObject().put("category", AppSettingCategories.MAIN_SCREEN))
        @Suppress("UNCHECKED_CAST")
        val settings = JsonUtils.toJSONObject(read["settings"] as Map<String, Any?>)
        val groups = settings.getJSONArray("zone_groups")
        if ((0 until groups.length()).any { groups.getString(it) == group }) return
        run("app_config.set", JSONObject()
            .put("category", AppSettingCategories.MAIN_SCREEN)
            .put("settings", JSONObject(settings.toString()).put("zone_groups", JSONArray(groups.toString()).put(group))))
    }

    private fun asset(path: String): JSONObject =
        JSONObject(context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() })

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (operation) {
            "install" -> s.shared("action_verbalize_demo_install")
            "remove" -> s.shared("action_verbalize_demo_remove")
            else -> s.shared("action_verbalize_unknown")
        }
    }
}
