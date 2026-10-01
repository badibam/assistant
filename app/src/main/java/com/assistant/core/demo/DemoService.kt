package com.assistant.core.demo

import android.content.Context
import androidx.room.withTransaction
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.AppSettingCategories
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.JsonUtils
import com.assistant.core.utils.LogManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * The demo (docs/design/demo.md), as the resource `demo`:
 * - install: removes the demo there is, then writes the one the app ships, dated now; its zone
 *   group joins the home screen's groups when it is not there yet, and never leaves them
 * - remove: removes the demo, what lives in it included
 *
 * The removal and the writing are one transaction: a failure writes nothing, and the demo there
 * was stays.
 */
class DemoService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)
    private val database = AppDatabase.getDatabase(context)

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
            DemoContent.read(asset("demo/structure.json"), asset("demo/${s.shared("demo_texts_file")}"), System.currentTimeMillis())
        } catch (e: Exception) {
            LogManager.service("Demo not read: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("demo_error_unreadable").format(e.message ?: ""))
        }
        addZoneGroup(content.group)?.let { return OperationResult.error(it) }
        database.withTransaction {
            removeAll()
            content.zones.forEach { database.zoneDao().insertZone(it) }
        }
        DataChangeNotifier.notifyZonesChanged()
        LogManager.service("Demo installed: ${content.zones.size} zones", "INFO")
        return OperationResult.success(mapOf("zones" to content.zones.size))
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
     * the zones again; nothing when it is there. Null when done, the refusal otherwise.
     */
    private suspend fun addZoneGroup(group: String): String? {
        val coordinator = Coordinator(context)
        val read = coordinator.processUserAction("app_config.get", mapOf("category" to AppSettingCategories.MAIN_SCREEN))
        @Suppress("UNCHECKED_CAST")
        val settings = (read.data?.get("settings") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
            ?: return read.error ?: s.shared("error_load_failed")
        val groups = settings.getJSONArray("zone_groups")
        if ((0 until groups.length()).any { groups.getString(it) == group }) return null
        val written = coordinator.processUserAction("app_config.set", mapOf(
            "category" to AppSettingCategories.MAIN_SCREEN,
            "settings" to JsonUtils.toMap(JSONObject(settings.toString()).put("zone_groups", JSONArray(groups.toString()).put(group)))
        ))
        return if (written.isSuccess) null else written.error ?: s.shared("error_operation_failed")
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
