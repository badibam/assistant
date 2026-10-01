package com.assistant.tools.list

import android.content.Context
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.tools.ToolScheduler
import com.assistant.core.utils.JsonUtils
import com.assistant.core.utils.LogManager
import org.json.JSONObject

/**
 * Notifies the due dates of the lists that have them (docs/design/list-due-dates.md): at each
 * pass, every item whose due date has come, unchecked and not notified yet, gets one
 * notification — the list as its title, the item as its text — and its due date written as
 * notified. That mark is what makes the item wait, the waiting's conditions knowing no "now".
 */
object ListDueScheduler : ToolScheduler {

    /** One pass at a time: two overlapping passes would both notify the same items. */
    private val passLock = kotlinx.coroutines.sync.Mutex()

    override suspend fun checkScheduled(context: Context) {
        if (!passLock.tryLock()) return
        try {
            val coordinator = Coordinator(context)
            val result = coordinator.processUserAction("tools.list_all", mapOf("include_config" to true))
            if (!result.isSuccess) {
                LogManager.service("ListDueScheduler: tools not listed: ${result.error}", "ERROR")
                return
            }
            @Suppress("UNCHECKED_CAST")
            val tools = (result.data?.get("tool_instances") as? List<Map<String, Any?>>).orEmpty().filter { it["tooltype"] == "list" }
            val now = System.currentTimeMillis()
            for (tool in tools) {
                @Suppress("UNCHECKED_CAST")
                val config = (tool["config"] as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) } ?: continue
                if (!ListToolType.hasDueDates(config)) continue
                // One list that fails must not stop the others
                try {
                    notifyDue(coordinator, tool["id"] as String, config, now)
                } catch (e: Exception) {
                    LogManager.service("ListDueScheduler: list ${tool["id"]} failed: ${e.message}", "ERROR", e)
                }
            }
        } finally {
            passLock.unlock()
        }
    }

    private suspend fun notifyDue(coordinator: Coordinator, toolInstanceId: String, config: JSONObject, now: Long) {
        val items = ListItems.load(coordinator, toolInstanceId)
        if (items == null) {
            LogManager.service("ListDueScheduler: items of $toolInstanceId not read", "ERROR")
            return
        }
        for (item in DueNotice.toNotify(items, now)) {
            val sent = coordinator.processUserAction("notifications.send", mapOf(
                "title" to config.getString("name"),
                "content" to item.name,
                "priority" to "default",
                "tool_instance_id" to toolInstanceId
            ))
            if (!sent.isSuccess) LogManager.service("ListDueScheduler: notification of ${item.id} failed: ${sent.error}", "WARN")
            // Marked even when the notification failed: the item waits all the same, and a
            // notification retried at every pass would come late and repeated
            val marked = coordinator.processUserAction("tool_data.update", mapOf(
                "id" to item.id,
                "state" to JSONObject().put(ListToolType.DUE_NOTIFIED, item.dueAt!!)
            ))
            if (!marked.isSuccess) LogManager.service("ListDueScheduler: ${item.id} not marked notified: ${marked.error}", "ERROR")
        }
    }
}
