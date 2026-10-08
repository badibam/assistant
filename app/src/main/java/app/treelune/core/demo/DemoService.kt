package app.treelune.core.demo

import android.content.Context
import androidx.room.withTransaction
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.LongOperation
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.Origin
import app.treelune.core.coordinator.Source
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.AppSettingCategories
import app.treelune.core.grid.Grid
import app.treelune.core.services.ExecutableService
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import app.treelune.core.ui.DisplayMode
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.JsonUtils
import app.treelune.core.utils.LogManager
import app.treelune.tools.goal.GoalDefinition
import app.treelune.tools.goal.GoalToolType
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The demo, as the resource `demo`:
 * - install: removes the demo there is, then builds the one the app ships through the services,
 *   as the app itself (Source.SYSTEM), which alone may give the demo's ids; its zone group joins
 *   the home screen's groups when it is not there yet
 * - remove: removes the demo, what lives in it included, and the zone groups its zones stood in
 *   that no zone stands in any more
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

    /** Both rewrite the whole demo: never two at once, nor with an import or a backup. */
    override val longOperations = setOf("install", "remove")

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        return when (operation) {
            "install" -> install()
            "remove" -> remove()
            else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        }
    }

    private suspend fun install(): OperationResult {
        val texts: JSONObject
        val content = try {
            texts = asset("demo/${s.shared("demo_texts_file")}")
            DemoContent.read(asset("demo/structure.json"), texts)
        } catch (e: Exception) {
            LogManager.service("Demo not read: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("demo_error_unreadable").format(e.message ?: ""))
        }
        LongOperation.at(s.shared("demo_phase_zones"))
        removeDemo()
        return try {
            addZoneGroup(content.group)
            build(content)
            fill(texts)
            automate(content)
            LogManager.service("Demo installed: ${content.zones.size} zones, ${content.tools.size} tools, ${content.variables.size} variables", "INFO")
            OperationResult.success(mapOf("zones" to content.zones.size, "tools" to content.tools.size, "variables" to content.variables.size))
        } catch (e: Refused) {
            removeDemo()
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
        var created = 0
        suspend fun create(tool: JSONObject) {
            LongOperation.at(s.shared("demo_phase_tools").format((created++).toString(), content.tools.size.toString()))
            run("tools.create", DemoContent.paramsOf(tool))
        }
        others.forEach { create(it) }
        content.variables.forEach { run("variables.create", it) }
        readingVariables.forEach { create(it) }
        content.tools.groupBy { it.getString("zone_id") to it.getJSONObject("config").optString("group").takeIf { g -> g.isNotEmpty() } }
            .forEach { (section, tools) ->
                run("tools.place", JSONObject()
                    .put("zone_id", section.first)
                    .apply { section.second?.let { put("group", it) } }
                    .put("places", places(tools)))
            }
    }

    /**
     * The automations, off — on, an automation calls the AI at the user's cost: each one's seed
     * session, its message the instruction, then the automation on it; none has a past. Their provider is the first one configured, or the first there is when
     * none is: switching one on then says the provider is not configured.
     */
    private suspend fun automate(content: DemoContent) {
        if (content.automations.isEmpty()) return
        LongOperation.at(s.shared("demo_phase_automations"))
        @Suppress("UNCHECKED_CAST")
        val providers = run("ai_provider_config.list", JSONObject())["providers"] as List<Map<String, Any?>>
        val provider = (providers.firstOrNull { it["is_configured"] == true } ?: providers.first())["id"] as String
        for (automation in content.automations) {
            val seed = automation.getString("seed_session_id")
            run("ai_sessions.create_session", JSONObject().put("id", seed).put("name", automation.getString("name")).put("type", "SEED").put("provider_id", provider))
            @Suppress("UNCHECKED_CAST")
            val message = (run("ai_sessions.list_messages", JSONObject().put("session_id", seed))["messages"] as List<Map<String, Any?>>).single()
            run("ai_sessions.update_message", JSONObject()
                .put("message_id", message["id"])
                .put("rich_content_json", app.treelune.core.ai.data.RichMessage(listOf(app.treelune.core.ai.data.MessageSegment.Text(automation.getString("seed")))).toJson()))
            run("automations.create", JSONObject(automation.toString()).apply { remove("seed") }.put("provider_id", provider))
        }
    }

    /**
     * The entries, afloat now: each tool's written in one batch, as the app; then the goals'
     * attempts, opened with their copy of the goal as the scheduler opens them, and those before
     * last week validated as the user validates them, judged on the demo's own entries.
     */
    private suspend fun fill(texts: JSONObject) {
        val data = DemoData(System.currentTimeMillis(), AppConfigManager.getDateTimeConfig().getZoneId(), asset("demo/entries.json"))
        val all = data.entries()
        val total = all.values.sumOf { it.size }
        var written = 0
        all.forEach { (tool, entries) ->
            if (entries.isEmpty()) return@forEach
            LongOperation.at(s.shared("demo_phase_entries").format(written.toString(), total.toString()))
            written += entries.size
            val batch = run("tool_data.batch_create", JSONObject()
                .put("tool_instance_id", tool)
                .put("entries", JSONArray(entries.map { DemoContent.resolve(it, texts) })))
            val failed = (batch["failed_count"] as? Number)?.toInt() ?: 0
            if (failed > 0) throw Refused("tool_data.batch_create $tool: $failed refused, ${batch["refusals"]}")
        }
        LongOperation.at(s.shared("demo_phase_goals"))
        for (attempt in data.attempts()) {
            @Suppress("UNCHECKED_CAST")
            val tool = run("tools.get", JSONObject().put("tool_instance_id", attempt.toolId))["tool_instance"] as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val config = JsonUtils.toJSONObject(tool["config"] as Map<String, Any?>)
            val id = "demo-e-attempt-${attempt.toolId}-${attempt.start}"
            run("tool_data.create", JSONObject()
                .put("id", id)
                .put("tool_instance_id", attempt.toolId)
                .put("name", config.getString("name"))
                .put("timestamp", attempt.start)
                .put("data", JSONObject(attempt.entered.toString()).put(GoalToolType.DEFINITION, GoalDefinition.copyOf(config).toString()))
                .put("state", JSONObject().put(GoalToolType.STATUS, attempt.status).put(GoalToolType.PERIOD_END, attempt.end)))
            // Validated by the user, as Camille would: a goal's validation is a person's or the AI's
            if (attempt.validate) withContext(Origin(Source.USER, byTheApp = true)) {
                val validated = coordinator.processUserAction("goal.validate", mapOf("id" to id))
                if (!validated.isSuccess) throw Refused("goal.validate $id: ${validated.error}")
            }
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
        removeDemo()
        DataChangeNotifier.notifyZonesChanged()
        LogManager.service("Demo removed", "INFO")
        return OperationResult.success()
    }

    /**
     * Everything of the demo, then the zone groups its zones stood in that are left empty — read
     * from the zones themselves, so a group named in another language than the demo's texts now
     * goes too. A group where a zone of the user's still stands stays.
     */
    private suspend fun removeDemo() {
        val demoGroups = database.zoneDao().getAllZones()
            .filter { it.id.startsWith(DemoContent.PREFIX) }
            .mapNotNull { it.group }.toSet()
        database.withTransaction { removeAll() }
        val stillUsed = database.zoneDao().getAllZones().mapNotNull { it.group }.toSet()
        removeZoneGroups(demoGroups - stillUsed)
    }

    /** Everything of the demo (DemoRemoval); called inside a transaction. */
    private fun removeAll() {
        val db = database.openHelper.writableDatabase
        DemoRemoval.STATEMENTS.forEach { db.execSQL(it) }
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

    /** Takes [groups] out of the home screen's zone groups through the settings service; nothing when none is there. */
    private suspend fun removeZoneGroups(groups: Set<String>) {
        if (groups.isEmpty()) return
        val read = run("app_config.get", JSONObject().put("category", AppSettingCategories.MAIN_SCREEN))
        @Suppress("UNCHECKED_CAST")
        val settings = JsonUtils.toJSONObject(read["settings"] as Map<String, Any?>)
        val before = settings.getJSONArray("zone_groups")
        val kept = (0 until before.length()).map { before.getString(it) }.filter { it !in groups }
        if (kept.size == before.length()) return
        run("app_config.set", JSONObject()
            .put("category", AppSettingCategories.MAIN_SCREEN)
            .put("settings", JSONObject(settings.toString()).put("zone_groups", JSONArray(kept))))
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
