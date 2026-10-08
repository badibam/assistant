package app.treelune.tools.list

import android.content.Context
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.services.ExecutableService
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolConfigSettings
import app.treelune.core.utils.JsonUtils
import org.json.JSONObject

/**
 * A list's operations, for the screen and the AI alike, beside the generic writes of its items:
 * - check: `id` — the item checked now; in a list set to remove what is checked, deleted instead
 * - uncheck: `id` — the item unchecked, when it was checked forgotten
 */
class ListService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(tool = "list", context = context)
    private val coordinator = Coordinator(context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        val checked = when (operation) {
            "check" -> true
            "uncheck" -> false
            else -> return OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        }
        val id = params.optString("id").takeIf { it.isNotEmpty() } ?: return OperationResult.error(s.shared("service_error_missing_id"))
        val removeWhenChecked = removeWhenChecked(id) ?: return OperationResult.error(s.shared("service_error_entry_not_found").format(id))
        val result = ListItems.setChecked(coordinator, id, checked, removeWhenChecked)
        if (!result.isSuccess) return OperationResult.error(result.error ?: "")
        return OperationResult.success(mapOf("id" to id, "checked" to checked, "deleted" to (checked && removeWhenChecked)))
    }

    /** Whether the list of the item [id] removes what is checked; null when there is no such item. */
    private suspend fun removeWhenChecked(id: String): Boolean? {
        val entry = coordinator.processUserAction("tool_data.get_single", mapOf("entry_id" to id)).data?.get("entry") as? Map<*, *> ?: return null
        val tool = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to entry["tool_instance_id"] as String))
        @Suppress("UNCHECKED_CAST")
        val config = ((tool.data?.get("tool_instance") as? Map<*, *>)?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
            ?: return null
        return ToolConfigSettings.read(ListToolType, config, context).boolean(ListToolType.REMOVE_WHEN_CHECKED)
    }

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(tool = "list", context = context)
        return s.tool("verbalize_$operation").takeIf { operation in setOf("check", "uncheck") } ?: s.shared("action_verbalize_unknown")
    }
}
